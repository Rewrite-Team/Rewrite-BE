package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.client.FirstReviewClient;
import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import com.daon.rewrite.reviewversion.client.FirstReviewResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReReviewJobWorker {

    private final ReReviewJobTransactionService transactionService;
    private final FirstReviewClient firstReviewClient;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        FirstReviewWork work = transactionService.start(jobId);
        if (work == null) {
            return;
        }

        try {
            List<FirstReviewResult> results = firstReviewClient.review(work.request());
            reviewVersionService.completeReReview(
                    jobId,
                    results.stream()
                            .map(result -> new ReviewQuestionResultInput(
                                    result.questionId(),
                                    result.aiReport(),
                                    result.rewrittenAnswer()
                            ))
                            .toList()
            );
        } catch (FirstReviewClientException exception) {
            transactionService.fail(jobId, exception.getReason());
        }
    }
}
