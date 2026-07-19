package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReReviewJobWorker {

    private final ReReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        FirstReviewWork work = transactionService.start(jobId);
        if (work == null) {
            return;
        }

        FirstReviewClientException.Reason failureReason = jobRunner.run(jobId, work.request());
        if (failureReason == null) {
            reviewVersionService.completeReReview(jobId);
        } else {
            transactionService.fail(jobId, failureReason);
        }
    }
}
