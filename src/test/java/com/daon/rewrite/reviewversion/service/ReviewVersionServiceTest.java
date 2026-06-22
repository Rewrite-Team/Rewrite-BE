package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class ReviewVersionServiceTest {

    @Autowired
    private ReviewVersionService service;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

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
    void completeFirstReviewPersistsOrderedSnapshotsAndCompletesResources() {
        Instant completedAt = Instant.parse("2026-06-21T05:00:00Z");
        saveProcessingReview("cl_1", "job_1", 2);
        given(idGenerator.generate("rv")).willReturn("rv_1");
        given(idGenerator.generate("rvqr")).willReturn("rvqr_1", "rvqr_2");
        given(clock.instant()).willReturn(completedAt);

        CompleteFirstReviewResult result = service.completeFirstReview(
                "job_1",
                List.of(
                        new ReviewQuestionResultInput("clq_2", " 두 번째 리포트 ", " 두 번째 수정본 "),
                        new ReviewQuestionResultInput("clq_1", " 첫 번째 리포트 ", " 첫 번째 수정본😀 ")
                )
        );

        assertThat(result.reviewVersion().getId()).isEqualTo("rv_1");
        assertThat(result.reviewVersion().getVersion()).isEqualTo("v0.1");
        assertThat(result.questionResults())
                .extracting(ReviewVersionQuestionResult::getId)
                .containsExactly("rvqr_1", "rvqr_2");
        assertThat(result.questionResults())
                .extracting(ReviewVersionQuestionResult::getQuestionOrder)
                .containsExactly(1, 2);
        assertThat(result.questionResults())
                .extracting(ReviewVersionQuestionResult::getAiReport)
                .containsExactly("첫 번째 리포트", "두 번째 리포트");
        assertThat(result.questionResults().getFirst().getRewrittenAnswer()).isEqualTo("첫 번째 수정본😀");
        assertThat(result.questionResults().getFirst().getRewrittenAnswerLength()).isEqualTo(9);
        assertThat(result.questionResults().getFirst().getFinalAnswer()).isEqualTo("첫 번째 수정본😀");

        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
            assertThat(coverLetter.getLatestReviewVersionId()).isEqualTo("rv_1");
            assertThat(coverLetter.getUpdatedAt()).isEqualTo(completedAt);
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getProgressCurrent()).isEqualTo(2);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.REVIEW_VERSION);
            assertThat(job.getResultRefId()).isEqualTo("rv_1");
            assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        });
    }

    @Test
    void completeFirstReviewRejectsInvalidQuestionMappingWithoutPartialChanges() {
        saveProcessingReview("cl_1", "job_1", 2);

        List<List<ReviewQuestionResultInput>> invalidInputs = List.of(
                List.of(new ReviewQuestionResultInput("clq_1", "리포트", "수정본")),
                List.of(
                        new ReviewQuestionResultInput("clq_1", "리포트", "수정본"),
                        new ReviewQuestionResultInput("clq_1", "리포트", "수정본")
                ),
                List.of(
                        new ReviewQuestionResultInput("clq_1", "리포트", "수정본"),
                        new ReviewQuestionResultInput("clq_unknown", "리포트", "수정본")
                )
        );

        for (List<ReviewQuestionResultInput> input : invalidInputs) {
            assertThatThrownBy(() -> service.completeFirstReview("job_1", input))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        assertUnchangedProcessingState();
    }

    @Test
    void completeFirstReviewRejectsBlankOrTooLongGeneratedTextWithoutPartialChanges() {
        saveProcessingReview("cl_1", "job_1", 2);

        assertThatThrownBy(() -> service.completeFirstReview(
                "job_1",
                List.of(
                        new ReviewQuestionResultInput("clq_1", " ", "수정본"),
                        new ReviewQuestionResultInput("clq_2", "리포트", "수정본")
                )
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> service.completeFirstReview(
                "job_1",
                List.of(
                        new ReviewQuestionResultInput("clq_1", "리포트", "가".repeat(1001)),
                        new ReviewQuestionResultInput("clq_2", "리포트", "수정본")
                )
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertUnchangedProcessingState();
    }

    @Test
    void completeFirstReviewReturnsExistingVersionWhenJobIsAlreadyCompleted() {
        saveProcessingReview("cl_1", "job_1", 1);
        given(idGenerator.generate("rv")).willReturn("rv_1");
        given(idGenerator.generate("rvqr")).willReturn("rvqr_1");
        given(clock.instant()).willReturn(Instant.parse("2026-06-21T05:00:00Z"));
        List<ReviewQuestionResultInput> input = List.of(
                new ReviewQuestionResultInput("clq_1", "리포트", "수정본")
        );

        CompleteFirstReviewResult first = service.completeFirstReview("job_1", input);
        CompleteFirstReviewResult second = service.completeFirstReview("job_1", input);

        assertThat(second.reviewVersion().getId()).isEqualTo(first.reviewVersion().getId());
        assertThat(reviewVersionRepository.count()).isEqualTo(1);
        assertThat(questionResultRepository.count()).isEqualTo(1);
    }

    @Test
    void completeFirstReviewRejectsJobThatIsNotProcessing() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = saveReviewingCoverLetter("cl_1", 1);
        llmJobRepository.save(LlmJob.pendingReview("job_pending", coverLetter.getId(), now, 1));

        assertThatThrownBy(() -> service.completeFirstReview(
                "job_pending",
                List.of(new ReviewQuestionResultInput("clq_1", "리포트", "수정본"))
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    private void saveProcessingReview(String coverLetterId, String jobId, int questionCount) {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = saveReviewingCoverLetter(coverLetterId, questionCount);
        LlmJob job = LlmJob.pendingReview(jobId, coverLetter.getId(), now, questionCount);
        job.startProcessing("첨삭을 시작합니다.");
        llmJobRepository.save(job);
    }

    private CoverLetter saveReviewingCoverLetter(String coverLetterId, int questionCount) {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", now);
        coverLetter.fillBasicInfo("제목", "회사", "직무", null, now);
        coverLetter.fillPreferences("우대사항", now);
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
        return coverLetter;
    }

    private void assertUnchangedProcessingState() {
        assertThat(reviewVersionRepository.count()).isZero();
        assertThat(questionResultRepository.count()).isZero();
        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter -> {
            assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWING);
            assertThat(coverLetter.getLatestReviewVersionId()).isNull();
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.PROCESSING);
            assertThat(job.getResultRefId()).isNull();
        });
    }
}
