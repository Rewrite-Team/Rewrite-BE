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
    void executeCompletesPendingFirstReviewJob() {
        Instant completedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReviewJob("cl_1", "job_1", 2);
        given(firstReviewClient.review(any())).willReturn(List.of(
                new FirstReviewResult("clq_2", "두 번째 리포트", "두 번째 수정본"),
                new FirstReviewResult("clq_1", "첫 번째 리포트", "첫 번째 수정본")
        ));
        given(idGenerator.generate("rv")).willReturn("rv_1");
        given(idGenerator.generate("rvqr")).willReturn("rvqr_1", "rvqr_2");
        given(clock.instant()).willReturn(completedAt);

        worker.execute("job_1");

        ArgumentCaptor<FirstReviewRequest> requestCaptor = ArgumentCaptor.forClass(FirstReviewRequest.class);
        then(firstReviewClient).should().review(requestCaptor.capture());
        FirstReviewRequest request = requestCaptor.getValue();
        assertThat(request.title()).isEqualTo("제목");
        assertThat(request.companyName()).isEqualTo("회사");
        assertThat(request.positionTitle()).isEqualTo("직무");
        assertThat(request.preferences()).isEqualTo("Spring Boot 경험");
        assertThat(request.questions()).extracting("questionId")
                .containsExactly("clq_1", "clq_2");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
            assertThat(coverLetter.getLatestReviewVersionId()).isEqualTo("rv_1");
            assertThat(coverLetter.getUpdatedAt()).isEqualTo(completedAt);
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.REVIEW_VERSION);
            assertThat(job.getResultRefId()).isEqualTo("rv_1");
            assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        });
        assertThat(questionResultRepository.findByReviewVersionIdOrderByQuestionOrderAsc("rv_1"))
                .extracting(result -> result.getQuestion().getId())
                .containsExactly("clq_1", "clq_2");
    }

    @Test
    void executeFailsJobAndCoverLetterWhenProviderFails() {
        Instant failedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReviewJob("cl_1", "job_1", 1);
        given(firstReviewClient.review(any()))
                .willThrow(FirstReviewClientException.providerError(new IllegalStateException("provider down")));
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter ->
                assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEW_FAILED)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 응답 생성에 실패했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(reviewVersionRepository.count()).isZero();
    }

    @Test
    void executeFailsJobAndCoverLetterWhenOutputValidationFails() {
        Instant failedAt = Instant.parse("2026-06-25T05:00:00Z");
        savePendingReviewJob("cl_1", "job_1", 1);
        given(firstReviewClient.review(any()))
                .willThrow(FirstReviewClientException.outputValidationFailed());
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter ->
                assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEW_FAILED)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_OUTPUT_VALIDATION_FAILED");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 출력 형식이 올바르지 않습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
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
