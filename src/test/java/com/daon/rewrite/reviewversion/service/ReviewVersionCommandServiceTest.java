package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class ReviewVersionCommandServiceTest {

    @Autowired
    private ReviewVersionCommandService service;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @Autowired
    private ReviewJobQuestionResultRepository jobQuestionResultRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private ReReviewJobWorker reReviewJobWorker;

    @BeforeEach
    void setUpIds() {
        given(idGenerator.generate("rjqr")).willReturn("rjqr_1", "rjqr_2");
    }

    @AfterEach
    void cleanUp() {
        questionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        jobQuestionResultRepository.deleteAll();
        llmJobRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void saveMyFinalAnswersUpdatesAllLatestQuestionResults() {
        Instant now = Instant.parse("2026-06-21T05:40:00Z");
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(clock.instant()).willReturn(now);

        SaveFinalAnswersResult result = service.saveMyFinalAnswers(
                "cl_1",
                "rv_1",
                List.of(
                        new SaveFinalAnswerInput("rvqr_2", " 두 번째 최종본 "),
                        new SaveFinalAnswerInput("rvqr_1", " 첫 번째 최종본😀 ")
                )
        );

        assertThat(result.coverLetterId()).isEqualTo("cl_1");
        assertThat(result.reviewVersionId()).isEqualTo("rv_1");
        assertThat(result.updatedAt()).isEqualTo(now);
        assertThat(result.questionResults()).extracting(ReviewVersionQuestionResult::getId)
                .containsExactly("rvqr_1", "rvqr_2");
        assertThat(result.questionResults()).extracting(ReviewVersionQuestionResult::getFinalAnswer)
                .containsExactly("첫 번째 최종본😀", "두 번째 최종본");
        assertThat(result.questionResults()).extracting(ReviewVersionQuestionResult::getFinalAnswerLength)
                .containsExactly(9, 8);

        assertThat(questionResultRepository.findById("rvqr_1")).hasValueSatisfying(questionResult -> {
            assertThat(questionResult.getFinalAnswer()).isEqualTo("첫 번째 최종본😀");
            assertThat(questionResult.getFinalAnswerLength()).isEqualTo(9);
        });
    }

    @Test
    void saveMyFinalAnswersRejectsNonLatestVersion() {
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_latest");
        ReviewVersion oldVersion = ReviewVersion.first(
                "rv_old",
                coverLetterRepository.findById("cl_1").orElseThrow(),
                Instant.parse("2026-06-21T04:00:00Z")
        );
        ReflectionTestUtils.setField(oldVersion, "version", "v0.0");
        reviewVersionRepository.save(oldVersion);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.saveMyFinalAnswers(
                "cl_1",
                oldVersion.getId(),
                List.of(new SaveFinalAnswerInput("rvqr_1", "최종본"))
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_VERSION_NOT_LATEST);
    }

    @Test
    void saveMyFinalAnswersRejectsInvalidAnswerSetWithoutPartialChanges() {
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        List<List<SaveFinalAnswerInput>> invalidInputs = List.of(
                List.of(new SaveFinalAnswerInput("rvqr_1", "첫 번째만")),
                List.of(
                        new SaveFinalAnswerInput("rvqr_1", "첫 번째"),
                        new SaveFinalAnswerInput("rvqr_1", "중복")
                ),
                List.of(
                        new SaveFinalAnswerInput("rvqr_1", "첫 번째"),
                        new SaveFinalAnswerInput("rvqr_unknown", "알 수 없음")
                ),
                List.of(
                        new SaveFinalAnswerInput("rvqr_1", " "),
                        new SaveFinalAnswerInput("rvqr_2", "두 번째")
                ),
                List.of(
                        new SaveFinalAnswerInput("rvqr_1", "가".repeat(5001)),
                        new SaveFinalAnswerInput("rvqr_2", "두 번째")
                )
        );

        for (List<SaveFinalAnswerInput> input : invalidInputs) {
            assertThatThrownBy(() -> service.saveMyFinalAnswers("cl_1", "rv_1", input))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        assertThat(questionResultRepository.findById("rvqr_1")).hasValueSatisfying(questionResult -> {
            assertThat(questionResult.getFinalAnswer()).isEqualTo("첫 번째 수정본");
            assertThat(questionResult.getFinalAnswerLength()).isEqualTo(8);
        });
    }

    @Test
    void saveMyFinalAnswersReportsDuplicateQuestionResultIdEvenWhenFirstAnswerIsBlank() {
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.saveMyFinalAnswers(
                        "cl_1",
                        "rv_1",
                        List.of(
                                new SaveFinalAnswerInput("rvqr_1", " "),
                                new SaveFinalAnswerInput("rvqr_1", "중복 답변")
                        )
                ))
                .satisfies(exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails())
                            .extracting("field")
                            .contains("answers[0].finalAnswer", "answers[1].questionResultId");
                });
    }

    @Test
    void saveMyFinalAnswersThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrDeleted() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        coverLetterRepository.save(deleted);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound("cl_missing");
        assertNotFound("cl_other");
        assertNotFound("cl_deleted");
    }

    @Test
    void saveMyFinalAnswersThrowsNotFoundWhenReviewVersionIsMissing() {
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.saveMyFinalAnswers(
                "cl_1",
                "rv_missing",
                List.of(
                        new SaveFinalAnswerInput("rvqr_1", "첫 번째"),
                        new SaveFinalAnswerInput("rvqr_2", "두 번째")
                )
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void requestMyReReviewCreatesPendingReReviewJobWithNormalizedInstruction() {
        Instant now = Instant.parse("2026-06-21T05:50:00Z");
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now);

        RequestReReviewResult result = service.requestMyReReview("cl_1", " 직무 키워드를 강조해주세요. ");

        assertThat(result.coverLetterId()).isEqualTo("cl_1");
        assertThat(result.job().getId()).isEqualTo("job_1");
        assertThat(result.job().getType()).isEqualTo(LlmJobType.COVER_LETTER_RE_REVIEW);
        assertThat(result.job().getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(result.job().getRequestInstruction()).isEqualTo("직무 키워드를 강조해주세요.");
        assertThat(result.job().getRequestRefId()).isEqualTo("rv_1");
        assertThat(result.job().getProgressTotal()).isEqualTo(2);
        assertThat(jobQuestionResultRepository.findByLlmJobIdOrderByQuestionOrderAsc("job_1"))
                .extracting(ReviewJobQuestionResult::getInputAnswer)
                .containsExactly("첫 번째 수정본", "두 번째 수정본");
        assertThat(coverLetterRepository.findById("cl_1")).hasValueSatisfying(coverLetter ->
                assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED));
    }

    @Test
    void requestMyReReviewNormalizesBlankInstructionToNull() {
        Instant now = Instant.parse("2026-06-21T05:50:00Z");
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now);

        RequestReReviewResult result = service.requestMyReReview("cl_1", " ");

        assertThat(result.job().getRequestInstruction()).isNull();
    }

    @Test
    void requestMyReReviewRejectsTooLongInstructionWithDetails() {
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.requestMyReReview("cl_1", "가".repeat(1001)))
                .satisfies(exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails())
                            .extracting("field")
                            .containsExactly("requestInstruction");
                });
    }

    @Test
    void requestMyReReviewRejectsMissingOtherOwnerDeletedOrNotReviewedCoverLetter() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        coverLetterRepository.save(deleted);
        coverLetterRepository.save(CoverLetter.draft("cl_draft", "user_1", now));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertReReviewNotFound("cl_missing");
        assertReReviewNotFound("cl_other");
        assertReReviewNotFound("cl_deleted");
        assertThatThrownBy(() -> service.requestMyReReview("cl_draft", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void requestMyReReviewRejectsWhenJobAlreadyRunning() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        saveReviewedCoverLetterWithLatestVersion("cl_1", "user_1", "rv_1");
        llmJobRepository.save(LlmJob.pendingReview("job_running", "cl_1", now.plusSeconds(60), 2));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.requestMyReReview("cl_1", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_JOB_ALREADY_RUNNING);
    }

    private void assertNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.saveMyFinalAnswers(
                coverLetterId,
                "rv_1",
                List.of(new SaveFinalAnswerInput("rvqr_1", "최종본"))
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void assertReReviewNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.requestMyReReview(coverLetterId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void saveReviewedCoverLetterWithLatestVersion(String coverLetterId, String ownerId, String versionId) {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, ownerId, now);
        coverLetterRepository.save(coverLetter);
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                versionId,
                coverLetter,
                now.plusSeconds(60)
        ));
        CoverLetterQuestion firstQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "지원 동기는?",
                1000,
                "첫 번째 원본"
        ));
        CoverLetterQuestion secondQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_2",
                coverLetter,
                2,
                "직무 역량은?",
                1500,
                "두 번째 원본"
        ));
        questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                firstQuestion,
                "첫 번째 리포트",
                "첫 번째 수정본"
        ));
        questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_2",
                reviewVersion,
                secondQuestion,
                "두 번째 리포트",
                "두 번째 수정본"
        ));
        coverLetter.completeReview(versionId, now.plusSeconds(120));
        coverLetterRepository.saveAndFlush(coverLetter);
    }
}
