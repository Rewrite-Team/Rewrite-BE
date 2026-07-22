package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.client.InterviewMessageFeedbackClient;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackClientException;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewMessageFeedbackJobWorker {

    private final InterviewMessageFeedbackJobTransactionService transactionService;
    private final InterviewMessageFeedbackClient client;
    private final LlmJobStreamService llmJobStreamService;

    public void execute(String jobId) {
        try {
            InterviewMessageFeedbackWork work = transactionService.start(jobId);
            if (work == null) {
                return;
            }
            InterviewMessageFeedbackResult result = client.generate(work.request());
            llmJobStreamService.publishValidatedFeedback(jobId, result.content());
            transactionService.complete(jobId, result);
        } catch (InterviewMessageFeedbackClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    private void failByClientException(String jobId, InterviewMessageFeedbackClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark interview message feedback job as failed. jobId={}", jobId, failureException);
        }
    }

    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected interview message feedback failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected interview message feedback failure. jobId={}", jobId, exception);
    }
}
