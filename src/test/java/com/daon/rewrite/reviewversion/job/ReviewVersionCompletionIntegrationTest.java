package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.ReviewClient;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import com.daon.rewrite.reviewversion.service.CompleteReviewResult;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.ReviewVersionCompletionService;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReviewVersionCompletionIntegrationTest {

    private static final Instant RESULT_COMPLETED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired private CoverLetterService coverLetterService;
    @Autowired private ReviewVersionCommandService commandService;
    @Autowired private ReviewVersionCompletionService completionService;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private LlmJobRepository jobRepository;
    @Autowired private ReviewVersionRepository versionRepository;
    @Autowired private ReviewVersionQuestionResultRepository resultRepository;
    @Autowired private ReviewJobQuestionResultRepository stagedResultRepository;
    @Autowired private FirstReviewJobTransactionService firstReviewTransactions;
    @Autowired private ReReviewJobTransactionService reReviewTransactions;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ReviewJobEventListener eventListener;
    @MockitoBean private ReviewClient reviewClient;

    @ParameterizedTest
    @EnumSource(value = LlmJobType.class, names = {"COVER_LETTER_REVIEW", "COVER_LETTER_RE_REVIEW"})
    void completionUsesStartedVersionAndRepeatedCompletionKeepsConfirmedResultIds(LlmJobType type) {
        ReviewAttempt attempt = createAttempt(type);
        long versionCount = versionRepository.countByCoverLetterId(attempt.coverLetterId());
        startProcessing(attempt);
        List<String> inputAnswers = stagedResults(attempt).stream()
                .map(ReviewJobQuestionResult::getInputAnswer)
                .toList();
        completeStagedResults(attempt);

        CompleteReviewResult completed = complete(attempt);

        assertThat(completed.reviewVersion().getId()).isEqualTo(attempt.versionId());
        assertThat(completed.questionResults()).extracting(ReviewVersionQuestionResult::getOriginalAnswer)
                .containsExactlyElementsOf(inputAnswers);
        assertThat(completed.questionResults()).extracting(ReviewVersionQuestionResult::getFinalAnswer)
                .containsExactly("새 수정본 1", "새 수정본 2");
        if (type == LlmJobType.COVER_LETTER_RE_REVIEW) {
            assertThat(inputAnswers).containsExactly("이전 최종 작성본 1", "이전 최종 작성본 2");
            assertThat(jobRepository.findById(attempt.jobId()).orElseThrow().getRequestRefId())
                    .isEqualTo(attempt.sourceVersionId());
            assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(attempt.sourceVersionId()))
                    .extracting(ReviewVersionQuestionResult::getFinalAnswer)
                    .containsExactlyElementsOf(inputAnswers);
        }
        CoverLetter coverLetter = coverLetterRepository.findById(attempt.coverLetterId()).orElseThrow();
        assertThat(coverLetter.getStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
        assertThat(coverLetter.getLatestReviewedVersionId()).isEqualTo(attempt.versionId());
        LlmJob job = jobRepository.findById(attempt.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(job.getProgressCurrent()).isEqualTo(job.getProgressTotal());
        assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.REVIEW_VERSION);
        assertThat(job.getResultRefId()).isEqualTo(attempt.versionId());
        assertThat(versionRepository.countByCoverLetterId(attempt.coverLetterId())).isEqualTo(versionCount);
        List<String> resultIds = completed.questionResults().stream()
                .map(ReviewVersionQuestionResult::getId).toList();
        CompletionState completedState = storedState(attempt);

        CompleteReviewResult repeated = complete(attempt);

        assertThat(repeated.reviewVersion().getId()).isEqualTo(attempt.versionId());
        assertThat(repeated.questionResults()).extracting(ReviewVersionQuestionResult::getId)
                .containsExactlyElementsOf(resultIds);
        assertThat(storedState(attempt)).isEqualTo(completedState);
    }

    @ParameterizedTest
    @EnumSource(value = LlmJobType.class, names = {"COVER_LETTER_REVIEW", "COVER_LETTER_RE_REVIEW"})
    void canceledCompletionReturnsNullAndPreservesStoredState(LlmJobType type) {
        ReviewAttempt attempt = createAttempt(type);
        startProcessing(attempt);
        completeStagedResults(attempt);
        coverLetterService.deleteMyCoverLetter(attempt.coverLetterId());
        CompletionState canceledState = storedState(attempt);

        assertThat(complete(attempt)).isNull();

        assertThat(storedState(attempt)).isEqualTo(canceledState);
        assertThat(versionRepository.findById(attempt.versionId()).orElseThrow().getStatus())
                .isEqualTo(LlmJobStatus.CANCELED);
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(attempt.versionId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = LlmJobType.class, names = {"COVER_LETTER_REVIEW", "COVER_LETTER_RE_REVIEW"})
    void pendingJobCannotBeCompleted(LlmJobType type) {
        ReviewAttempt attempt = createAttempt(type);

        assertRejectedWithoutStoredChanges(attempt);
    }

    @Test
    void failedJobCannotBeCompletedEvenWhenAllStagedQuestionsSucceeded() {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_REVIEW);
        startProcessing(attempt);
        completeStagedResults(attempt);
        firstReviewTransactions.fail(attempt.jobId(), ReviewClientException.Reason.PROVIDER_ERROR);

        assertRejectedWithoutStoredChanges(attempt);
    }

    @Test
    void firstReviewRejectsAnIncompleteStagedQuestion() {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_REVIEW);
        startProcessing(attempt);
        ReviewJobQuestionResult firstQuestion = stagedResults(attempt).getFirst();
        firstQuestion.complete("문항 리포트", "새 수정본 1", RESULT_COMPLETED_AT);
        stagedResultRepository.save(firstQuestion);

        assertRejectedWithoutStoredChanges(attempt);
    }

    @Test
    void reReviewRejectsMissingStagedQuestions() {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_RE_REVIEW);
        startProcessing(attempt);
        completeStagedResults(attempt);
        stagedResultRepository.deleteById(stagedResults(attempt).getLast().getId());

        assertRejectedWithoutStoredChanges(attempt);
    }

    @Test
    void reReviewRequiresLatestSuccessfulVersionBeforeConfirmingResults() {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_RE_REVIEW);
        startProcessing(attempt);
        completeStagedResults(attempt);
        jdbcTemplate.update("update cover_letters set latest_review_version_id = null where id = ?",
                attempt.coverLetterId());

        assertRejectedWithoutStoredChanges(attempt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"request_ref_type", "request_ref_id"})
    void reReviewRequiresBothInputReferenceTypeAndId(String missingColumn) {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_RE_REVIEW);
        startProcessing(attempt);
        completeStagedResults(attempt);
        jdbcTemplate.update("update llm_jobs set " + missingColumn + " = null where id = ?", attempt.jobId());

        assertRejectedWithoutStoredChanges(attempt);
    }

    @Test
    void reReviewRejectsInputVersionFromAnotherCoverLetter() {
        ReviewAttempt attempt = createAttempt(LlmJobType.COVER_LETTER_RE_REVIEW);
        startProcessing(attempt);
        completeStagedResults(attempt);
        ReviewAttempt otherAttempt = createAttempt(LlmJobType.COVER_LETTER_RE_REVIEW);
        jdbcTemplate.update("update llm_jobs set request_ref_id = ? where id = ?",
                otherAttempt.sourceVersionId(), attempt.jobId());

        assertRejectedWithoutStoredChanges(attempt);
    }

    private void assertRejectedWithoutStoredChanges(ReviewAttempt attempt) {
        CompletionState before = storedState(attempt);

        assertThatThrownBy(() -> complete(attempt))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));

        assertThat(storedState(attempt)).isEqualTo(before);
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(attempt.versionId())).isEmpty();
    }

    private ReviewAttempt createAttempt(LlmJobType type) {
        CoverLetter coverLetter = coverLetterService.create();
        coverLetterService.saveBasicInfo(coverLetter.getId(), "백엔드 지원", "회사", "개발자", null);
        coverLetterService.savePreferences(coverLetter.getId(), "Spring 개발 경험");
        coverLetterService.saveQuestions(coverLetter.getId(), List.of(
                new SaveQuestionInput("문항 1", 1000, "원본 답변 1"),
                new SaveQuestionInput("문항 2", 1000, "원본 답변 2")
        ));
        LlmJob firstJob = coverLetterService.submit(coverLetter.getId()).job();
        String firstVersionId = versionRepository.findByLlmJobId(firstJob.getId()).orElseThrow().getId();
        ReviewAttempt firstAttempt = new ReviewAttempt(
                LlmJobType.COVER_LETTER_REVIEW, coverLetter.getId(), firstJob.getId(), firstVersionId, null
        );
        if (type == LlmJobType.COVER_LETTER_REVIEW) {
            return firstAttempt;
        }
        startProcessing(firstAttempt);
        completeStagedResults(firstAttempt);
        CompleteReviewResult firstCompletion = completionService.completeFirstReview(firstJob.getId());
        commandService.saveMyFinalAnswers(coverLetter.getId(), firstVersionId,
                firstCompletion.questionResults().stream()
                        .map(result -> new SaveFinalAnswerInput(
                                result.getId(), "이전 최종 작성본 " + result.getQuestionOrder()
                        ))
                        .toList());
        LlmJob reReviewJob = commandService.requestMyReReview(coverLetter.getId(), "경험을 구체화해주세요.").job();
        String reReviewVersionId = versionRepository.findByLlmJobId(reReviewJob.getId()).orElseThrow().getId();
        return new ReviewAttempt(type, coverLetter.getId(), reReviewJob.getId(), reReviewVersionId, firstVersionId);
    }

    private void startProcessing(ReviewAttempt attempt) {
        if (attempt.type() == LlmJobType.COVER_LETTER_REVIEW) {
            firstReviewTransactions.start(attempt.jobId());
        } else {
            reReviewTransactions.start(attempt.jobId());
        }
    }

    private List<ReviewJobQuestionResult> stagedResults(ReviewAttempt attempt) {
        return stagedResultRepository.findByLlmJobIdOrderByQuestionOrderAsc(attempt.jobId());
    }

    private void completeStagedResults(ReviewAttempt attempt) {
        List<ReviewJobQuestionResult> results = stagedResults(attempt);
        results.forEach(result -> result.complete(
                "문항 리포트 " + result.getQuestionOrder(),
                "새 수정본 " + result.getQuestionOrder(),
                RESULT_COMPLETED_AT
        ));
        stagedResultRepository.saveAll(results);
    }

    private CompleteReviewResult complete(ReviewAttempt attempt) {
        return attempt.type() == LlmJobType.COVER_LETTER_REVIEW
                ? completionService.completeFirstReview(attempt.jobId())
                : completionService.completeReReview(attempt.jobId());
    }

    private CompletionState storedState(ReviewAttempt attempt) {
        CoverLetter coverLetter = coverLetterRepository.findById(attempt.coverLetterId()).orElseThrow();
        LlmJob job = jobRepository.findById(attempt.jobId()).orElseThrow();
        List<String> resultIds = resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(attempt.versionId())
                .stream().map(ReviewVersionQuestionResult::getId).toList();
        return new CompletionState(
                coverLetter.getStatus(), coverLetter.getLatestReviewedVersionId(), coverLetter.getUpdatedAt(),
                job.getStatus(), job.getResultRefType(), job.getResultRefId(), job.getCompletedAt(),
                versionRepository.countByCoverLetterId(attempt.coverLetterId()), resultIds
        );
    }

    private record ReviewAttempt(
            LlmJobType type, String coverLetterId, String jobId, String versionId, String sourceVersionId
    ) {
    }

    private record CompletionState(
            CoverLetterStatus coverLetterStatus,
            String latestReviewedVersionId,
            Instant coverLetterUpdatedAt,
            LlmJobStatus jobStatus,
            LlmJobResultRefType resultRefType,
            String resultRefId,
            Instant jobCompletedAt,
            long versionCount,
            List<String> confirmedResultIds
    ) {
    }
}
