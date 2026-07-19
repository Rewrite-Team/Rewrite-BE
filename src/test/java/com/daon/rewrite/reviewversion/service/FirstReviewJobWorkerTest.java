package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.FirstReviewClient;
import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import com.daon.rewrite.reviewversion.client.FirstReviewResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
class FirstReviewJobWorkerTest {

    @Autowired
    private FirstReviewJobWorker worker;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository versionQuestionResultRepository;

    @Autowired
    private ReviewJobQuestionResultRepository jobQuestionResultRepository;

    @MockitoBean
    private FirstReviewClient firstReviewClient;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        versionQuestionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        jobQuestionResultRepository.deleteAll();
        llmJobRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void executesQuestionsInParallelAndFinalizesFromStagedResults() throws Exception {
        Instant completedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReviewJob("cl_1", "job_1", 2);
        CountDownLatch concurrentCalls = new CountDownLatch(2);
        given(firstReviewClient.reviewQuestion(any(), anyString())).willAnswer(invocation -> {
            concurrentCalls.countDown();
            assertThat(concurrentCalls.await(1, TimeUnit.SECONDS)).isTrue();
            String questionId = invocation.getArgument(1);
            return new FirstReviewResult(questionId, questionId + " 리포트", questionId + " 수정본");
        });
        given(idGenerator.generate("rjqr")).willReturn("rjqr_1", "rjqr_2");
        given(idGenerator.generate("rv")).willReturn("rv_1");
        given(idGenerator.generate("rvqr")).willReturn("rvqr_1", "rvqr_2");
        given(clock.instant()).willReturn(completedAt);

        worker.execute("job_1");
        worker.execute("job_1");

        then(firstReviewClient).should(times(2)).reviewQuestion(any(), anyString());
        assertThat(jobQuestionResultRepository.findByLlmJobIdOrderByQuestionOrderAsc("job_1"))
                .extracting(ReviewJobQuestionResult::getStatus)
                .containsExactly(
                        ReviewJobQuestionResultStatus.COMPLETED,
                        ReviewJobQuestionResultStatus.COMPLETED
                );
        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
            assertThat(coverLetter.getLatestReviewVersionId()).isEqualTo("rv_1");
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getProgressCurrent()).isEqualTo(2);
            assertThat(job.getAttempt()).isEqualTo(1);
        });
        assertThat(versionQuestionResultRepository.findByReviewVersionIdOrderByQuestionOrderAsc("rv_1"))
                .extracting(result -> result.getQuestion().getId())
                .containsExactly("clq_1", "clq_2");
    }

    @Test
    void retriesOnlyFailedQuestionOnceThenFailsWithoutCreatingVersion() {
        Instant failedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReviewJob("cl_1", "job_1", 1);
        given(firstReviewClient.reviewQuestion(any(), anyString()))
                .willThrow(FirstReviewClientException.providerError(new IllegalStateException("provider down")));
        given(idGenerator.generate("rjqr")).willReturn("rjqr_1");
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        then(firstReviewClient).should(times(2)).reviewQuestion(any(), anyString());
        assertThat(jobQuestionResultRepository.findByLlmJobIdOrderByQuestionOrderAsc("job_1"))
                .extracting(ReviewJobQuestionResult::getStatus)
                .containsExactly(ReviewJobQuestionResultStatus.FAILED);
        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter ->
                assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEW_FAILED)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getAttempt()).isEqualTo(2);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
        });
        assertThat(reviewVersionRepository.count()).isZero();
    }

    private void savePendingReviewJob(String coverLetterId, String jobId, int questionCount) {
        Instant now = Instant.parse("2026-06-25T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", now);
        coverLetter.fillBasicInfo("제목", "회사", "직무", "https://example.com/jobs/1", now);
        coverLetter.fillPreferences("Spring Boot 경험", now);
        coverLetter.startReview(now);
        coverLetterRepository.save(coverLetter);

        for (int index = 1; index <= questionCount; index++) {
            questionRepository.save(CoverLetterQuestion.create(
                    "clq_" + index,
                    coverLetter,
                    index,
                    "질문 " + index,
                    1000,
                    "원본 답변 " + index
            ));
        }
        llmJobRepository.save(LlmJob.pendingReview(jobId, coverLetterId, now, questionCount));
    }
}
