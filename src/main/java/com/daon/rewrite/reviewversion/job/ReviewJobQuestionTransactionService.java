package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.ReviewResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 병렬 문항 task의 DB 반영을 Job 행 잠금으로 직렬화한다.
 * 문항 결과와 완료 진행률을 같은 트랜잭션으로 변경해 중복 반영과 진행률 갱신 유실을 막는다.
 * 취소 등으로 PROCESSING을 벗어난 Job에는 늦게 도착한 결과를 반영하지 않는다.
 */
@Service
@RequiredArgsConstructor
class ReviewJobQuestionTransactionService {

    private final LlmJobRepository llmJobRepository;
    private final ReviewJobQuestionResultRepository questionResultRepository;
    private final Clock clock;

    /**
     * 검증된 한 문항을 임시 결과에 저장하고 진행률을 하나 증가시킨다.
     * @return 새로 완료했으면 true, Job이 PROCESSING이 아니거나 이미 완료한 문항이면 false
     */
    @Transactional
    public boolean completeQuestion(String jobId, ReviewResult result) {
        LlmJob job = findReviewJobForUpdate(jobId);
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            return false;
        }

        ReviewJobQuestionResult questionResult = findQuestionResult(jobId, result.questionId());
        if (questionResult.getStatus() == ReviewJobQuestionResultStatus.COMPLETED) {
            return false;
        }
        if (questionResult.getStatus() != ReviewJobQuestionResultStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        questionResult.complete(result.aiReport(), result.rewrittenAnswer(), Instant.now(clock));
        job.advanceProgress((job.getProgressCurrent() + 1) + "개 문항 첨삭이 완료되었습니다.");
        return true;
    }

    /** 재시도까지 실패한 문항만 표시한다. Job 전체의 실패 전환은 Runner의 집계를 받은 Worker가 담당한다. */
    @Transactional
    public void failQuestion(String jobId, String questionId) {
        LlmJob job = findReviewJobForUpdate(jobId);
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            return;
        }
        findQuestionResult(jobId, questionId).fail(Instant.now(clock));
    }

    /** Job에 재시도 사실을 기록한다. 문항별 실제 호출 횟수 제한은 Runner의 반복문이 담당한다. */
    @Transactional
    public void markRetry(String jobId) {
        LlmJob job = findReviewJobForUpdate(jobId);
        if (job.getStatus() == LlmJobStatus.PROCESSING) {
            job.markRetried();
        }
    }

    private LlmJob findReviewJobForUpdate(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (job.getType() != LlmJobType.COVER_LETTER_REVIEW
                && job.getType() != LlmJobType.COVER_LETTER_RE_REVIEW) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private ReviewJobQuestionResult findQuestionResult(String jobId, String questionId) {
        return questionResultRepository.findByLlmJobIdAndQuestionId(jobId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }
}
