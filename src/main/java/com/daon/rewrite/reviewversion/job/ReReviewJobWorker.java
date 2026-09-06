package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.service.ReviewVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReReviewJobWorker {

    private final ReReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        try {
            ReviewWork work = transactionService.start(jobId);
            if (work == null) {
                return;
            }
            Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, work.request());
            if (failureReason.isPresent()) {
                transactionService.fail(jobId, failureReason.get());
                return;
            }
            reviewVersionService.completeReReview(jobId);
        } catch (RuntimeException exception) {
            try {
                transactionService.failUnexpected(jobId);
            } catch (RuntimeException failureException) {
                log.error("재첨삭 실패 상태 저장에 실패했습니다. jobId={}", jobId, failureException);
            }
            log.error("재첨삭 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}", jobId, exception);
        }
    }
}
