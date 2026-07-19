package com.daon.rewrite.reviewversion.service;

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

@Service
@RequiredArgsConstructor
class ReviewJobQuestionTransactionService {

    private final LlmJobRepository llmJobRepository;
    private final ReviewJobQuestionResultRepository questionResultRepository;
    private final Clock clock;

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

    @Transactional
    public void failQuestion(String jobId, String questionId) {
        LlmJob job = findReviewJobForUpdate(jobId);
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            return;
        }
        findQuestionResult(jobId, questionId).fail(Instant.now(clock));
    }

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
