package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FirstReviewJobWorker {

    private final FirstReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        FirstReviewWork work = transactionService.start(jobId);
        if (work == null) {
            return;
        }

        FirstReviewClientException.Reason failureReason = jobRunner.run(jobId, work.request());
        if (failureReason == null) {
            reviewVersionService.completeFirstReview(jobId);
        } else {
            transactionService.fail(jobId, failureReason);
        }
    }
}
