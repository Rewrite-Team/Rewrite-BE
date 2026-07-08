package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClient;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClientException;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordAnalysisJobWorker {

    private static final Logger log = LoggerFactory.getLogger(KeywordAnalysisJobWorker.class);

    private final KeywordAnalysisJobTransactionService transactionService;
    private final KeywordAnalysisClient keywordAnalysisClient;

    public void execute(String jobId) {
        try {
            KeywordAnalysisWork work = transactionService.start(jobId);
            if (work == null) {
                return;
            }
            List<KeywordAnalysisResult> results = keywordAnalysisClient.analyze(work.request());
            transactionService.complete(jobId, results);
        } catch (KeywordAnalysisClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    private void failByClientException(String jobId, KeywordAnalysisClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark keyword analysis job as failed. jobId={}", jobId, failureException);
        }
    }

    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected keyword analysis job failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected keyword analysis job failure. jobId={}", jobId, exception);
    }
}
