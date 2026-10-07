package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.service.CoverLetterJobLockService;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.client.ReviewQuestion;
import com.daon.rewrite.reviewversion.client.ReviewRequest;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 재첨삭의 시작·실패 전이를 자기소개서와 Job의 행 잠금 안에서 처리한다.
 * 답변 입력은 요청 트랜잭션에서 저장한 최신 성공 버전의 finalAnswer 스냅샷을 사용한다.
 * 이번 시도의 실패는 이전 성공 버전과 성공한 임시 문항 결과를 보존한다.
 */
@Service
@RequiredArgsConstructor
class ReReviewJobTransactionService {

    private static final String STARTED_MESSAGE = "재첨삭을 시작합니다.";
    private static final String FAILED_MESSAGE = "LLM 재첨삭에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "재첨삭 처리 중 오류가 발생했습니다.";

    private final CoverLetterJobLockService coverLetterJobLockService;
    private final ReviewJobQuestionResultRepository questionResultRepository;
    private final Clock clock;

    /**
     * 요청 시 고정한 문항 입력·요구사항과 현재 자기소개서의 공통 문맥을 읽고 PROCESSING으로 전환한다.
     * Worker 실행 시 최신 버전을 다시 선택하지 않으며, 이미 시작했거나 종료된 Job이면 null을 반환한다.
     */
    @Transactional
    public ReviewWork start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateReReviewJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWING
                || coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        if (job.getRequestRefType() != LlmJobRequestRefType.REVIEW_VERSION
                || job.getRequestRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // requestRef는 입력 버전의 출처이고, 실제 LLM 입력은 Job 생성 때 함께 저장한 문항 스냅샷이다.
        List<ReviewJobQuestionResult> questionResults = questionResultRepository
                .findByLlmJobIdOrderByQuestionOrderAsc(job.getId());
        if (questionResults.isEmpty() || questionResults.size() != job.getProgressTotal()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new ReviewWork(new ReviewRequest(
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getJobPostingUrl(),
                coverLetter.getPreferences(),
                job.getRequestInstruction(),
                questionResults.stream()
                        .map(result -> new ReviewQuestion(
                                result.getQuestion().getId(),
                                result.getQuestionOrder(),
                                result.getQuestionText(),
                                result.getMaxAnswerLength(),
                                result.getInputAnswer()
                        ))
                        .toList()
        ));
    }

    /** 이번 Job·표시 상태만 실패로 전환한다. latestReviewedVersionId와 성공 문항 결과는 유지한다. */
    @Transactional
    public void fail(String jobId, ReviewClientException.Reason reason) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateReReviewJob(locked.job());
        if (job.getStatus().isTerminal()) {
            return;
        }

        CoverLetter coverLetter = locked.coverLetter();

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                now
        );
        coverLetter.failReview(now);
    }

    /** 예상 밖 오류는 내부 오류로 기록하며, 취소·완료·실패 등 이미 종료된 Job은 변경하지 않는다. */
    @Transactional
    public void failUnexpected(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateReReviewJob(locked.job());
        if (job.getStatus().isTerminal()) {
            return;
        }
        CoverLetter coverLetter = locked.coverLetter();
        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_MESSAGE,
                now
        );
        coverLetter.failReview(now);
    }

    private LlmJob validateReReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_RE_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private String errorCode(ReviewClientException.Reason reason) {
        if (reason == ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(ReviewClientException.Reason reason) {
        if (reason == ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }
}
