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
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.service.ReviewVersionCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 최초 첨삭의 입력 준비·PROCESSING 전환과 실패 상태 저장을 짧은 트랜잭션으로 수행한다.
 * 자기소개서와 Job을 잠근 뒤 상태를 확인해 중복 시작과 취소 후 상태 덮어쓰기를 막는다.
 * 성공한 임시 문항 결과와 요청 시 생성한 버전은 전체 Job이 실패해도 보존한다.
 */
@Service
@RequiredArgsConstructor
class FirstReviewJobLifecycleService implements ReviewJobLifecycle {

    private static final String STARTED_MESSAGE = "첨삭을 시작합니다.";
    private static final String FAILED_MESSAGE = "LLM 첨삭에 실패했습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "첨삭 처리 중 오류가 발생했습니다.";

    private final CoverLetterJobLockService coverLetterJobLockService;
    private final ReviewJobQuestionResultRepository jobQuestionResultRepository;
    private final Clock clock;
    private final ReviewVersionCompletionService reviewVersionCompletionService;

    @Override
    public LlmJobType jobType() {
        return LlmJobType.COVER_LETTER_REVIEW;
    }

    /**
     * 제출 때 저장한 문항별 임시 입력을 읽어 외부 호출에 필요한 값만 반환한다.
     * 재사용 문항은 호출 대상에서 제외하며, 이미 시작했거나 종료된 Job이면 null을 반환한다.
     */
    @Override
    @Transactional
    public ReviewJobInput start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateFirstReviewJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 제출 트랜잭션에서 고정한 문항 입력과 직전 실패 시도에서 이어받은 성공 문항을 읽는다.
        List<ReviewJobQuestionResult> questionResults = jobQuestionResultRepository
                .findByLlmJobIdOrderByQuestionOrderAsc(job.getId());
        if (questionResults.isEmpty() || questionResults.size() != job.getProgressTotal()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new ReviewJobInput(
                new ReviewRequest(
                        coverLetter.getTitle(),
                        coverLetter.getCompanyName(),
                        coverLetter.getPositionTitle(),
                        coverLetter.getJobPostingUrl(),
                        coverLetter.getPreferences(),
                        questionResults.stream()
                                .map(result -> new ReviewQuestion(
                                        result.getQuestion().getId(),
                                        result.getQuestionOrder(),
                                        result.getQuestionText(),
                                        result.getMaxAnswerLength(),
                                        result.getInputAnswer()
                                ))
                                .toList()
                ),
                questionResults.stream()
                        .filter(result -> result.getStatus() == ReviewJobQuestionResultStatus.PROCESSING)
                        .map(result -> result.getQuestion().getId())
                        .collect(Collectors.toSet())
        );
    }

    /** 완료 문항 수를 유지한 채 Job·자기소개서를 함께 실패로 전환한다. 이미 종료된 Job은 변경하지 않는다. */
    @Override
    @Transactional
    public void fail(String jobId, ReviewClientException.Reason reason) {
        markFailed(jobId, reason.failureKind().errorCode(), reason.failureKind().errorMessage());
    }

    /** 분류된 LLM 실패 외의 오류를 내부 오류로 기록하며, 이미 확정된 종료 상태는 유지한다. */
    @Override
    @Transactional
    public void failUnexpected(String jobId) {
        markFailed(jobId, UNEXPECTED_ERROR_CODE, UNEXPECTED_ERROR_MESSAGE);
    }

    /** 전체 확정은 ReviewVersionCompletionService가 자기 트랜잭션에서 수행한다. */
    @Override
    public void complete(String jobId) {
        reviewVersionCompletionService.completeFirstReview(jobId);
    }

    private void markFailed(String jobId, String errorCode, String errorMessage) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateFirstReviewJob(locked.job());
        if (job.getStatus().isTerminal()) {
            return;
        }
        CoverLetter coverLetter = locked.coverLetter();
        Instant now = Instant.now(clock);
        job.markFailed(job.getProgressCurrent(), FAILED_MESSAGE, errorCode, errorMessage, now);
        coverLetter.failReview(now);
    }

    private LlmJob validateFirstReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }
}
