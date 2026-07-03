package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.FirstReviewClient;
import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import com.daon.rewrite.reviewversion.client.FirstReviewRequest;
import com.daon.rewrite.reviewversion.client.FirstReviewResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@ActiveProfiles("test")
class ReReviewJobWorkerTest {

    @Autowired
    private ReReviewJobWorker worker;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @MockitoBean
    private FirstReviewClient firstReviewClient;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        questionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        llmJobRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void executeCompletesPendingReReviewJobFromLatestFinalAnswers() {
        Instant completedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReReviewJob("cl_1", "job_1", "직무 키워드를 강조해주세요.");
        given(firstReviewClient.review(any())).willReturn(List.of(
                new FirstReviewResult("clq_2", "두 번째 새 리포트", "두 번째 새 수정본"),
                new FirstReviewResult("clq_1", "첫 번째 새 리포트", "첫 번째 새 수정본")
        ));
        given(idGenerator.generate("rv")).willReturn("rv_2");
        given(idGenerator.generate("rvqr")).willReturn("rvqr_3", "rvqr_4");
        given(clock.instant()).willReturn(completedAt);

        worker.execute("job_1");

        ArgumentCaptor<FirstReviewRequest> requestCaptor = ArgumentCaptor.forClass(FirstReviewRequest.class);
        then(firstReviewClient).should().review(requestCaptor.capture());
        FirstReviewRequest request = requestCaptor.getValue();
        assertThat(request.requestInstruction()).isEqualTo("직무 키워드를 강조해주세요.");
        assertThat(request.questions())
                .extracting("originalAnswer")
                .containsExactly("첫 번째 최종본", "두 번째 최종본");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
            assertThat(coverLetter.getLatestReviewVersionId()).isEqualTo("rv_2");
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.REVIEW_VERSION);
            assertThat(job.getResultRefId()).isEqualTo("rv_2");
            assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        });
        assertThat(questionResultRepository.findByReviewVersionIdOrderByQuestionOrderAsc("rv_2"))
                .extracting(ReviewVersionQuestionResult::getOriginalAnswer)
                .containsExactly("첫 번째 최종본", "두 번째 최종본");
    }

    @Test
    void executeFailsOnlyJobWhenProviderFails() {
        Instant failedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReReviewJob("cl_1", "job_1", "직무 키워드를 강조해주세요.");
        given(firstReviewClient.review(any()))
                .willThrow(FirstReviewClientException.providerError(new IllegalStateException("provider down")));
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
            assertThat(coverLetter.getLatestReviewVersionId()).isEqualTo("rv_1");
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(reviewVersionRepository.count()).isEqualTo(1);
    }

    private void savePendingReReviewJob(String coverLetterId, String jobId, String requestInstruction) {
        Instant now = Instant.parse("2026-06-25T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", now);
        coverLetter.fillBasicInfo("제목", "회사", "직무", "https://example.com/jobs/1", now);
        coverLetter.fillPreferences("Spring Boot 경험", now);
        coverLetterRepository.save(coverLetter);

        ReviewVersion latestVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_1",
                coverLetter,
                now.plusSeconds(60)
        ));
        CoverLetterQuestion firstQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "질문 1",
                1000,
                "첫 번째 원본"
        ));
        CoverLetterQuestion secondQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_2",
                coverLetter,
                2,
                "질문 2",
                1000,
                "두 번째 원본"
        ));
        ReviewVersionQuestionResult firstResult = ReviewVersionQuestionResult.create(
                "rvqr_1",
                latestVersion,
                firstQuestion,
                "첫 번째 리포트",
                "첫 번째 수정본"
        );
        firstResult.updateFinalAnswer("첫 번째 최종본");
        ReviewVersionQuestionResult secondResult = ReviewVersionQuestionResult.create(
                "rvqr_2",
                latestVersion,
                secondQuestion,
                "두 번째 리포트",
                "두 번째 수정본"
        );
        secondResult.updateFinalAnswer("두 번째 최종본");
        questionResultRepository.saveAll(List.of(firstResult, secondResult));

        coverLetter.completeReview(latestVersion.getId(), now.plusSeconds(120));
        coverLetterRepository.saveAndFlush(coverLetter);
        llmJobRepository.save(LlmJob.pendingReReview(jobId, coverLetterId, requestInstruction, now.plusSeconds(180), 2));
    }
}
