package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.client.InterviewQuestionGenerationClient;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationClientException;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionGenerationJobWorker {

    private final InterviewQuestionGenerationJobTransactionService transactionService;
    private final InterviewQuestionGenerationClient client;

    public void execute(String jobId) {
        try {
            InterviewQuestionGenerationWork work = transactionService.start(jobId);
            if (work == null) {
                return;
            }
            List<InterviewQuestionGenerationResult> results = client.generate(work.request());
            transactionService.complete(jobId, results);
        } catch (InterviewQuestionGenerationClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    private void failByClientException(String jobId, InterviewQuestionGenerationClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark interview question generation job as failed. jobId={}", jobId, failureException);
        }
    }

    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected interview question generation failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected interview question generation failure. jobId={}", jobId, exception);
    }
}
