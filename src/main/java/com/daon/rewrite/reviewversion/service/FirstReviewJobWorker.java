package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.client.ReviewClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FirstReviewJobWorker {

    private final FirstReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        ReviewWork work = transactionService.start(jobId);
        if (work == null) {
            return;
        }

        Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, work.request());
        if (failureReason.isPresent()) {
            transactionService.fail(jobId, failureReason.get());
            return;
        }

        reviewVersionService.completeFirstReview(jobId);
    }
}
