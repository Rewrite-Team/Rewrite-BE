package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClient;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClientException;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordAnalysisJobWorker {

    private final KeywordAnalysisJobTransactionService transactionService;
    private final KeywordAnalysisClient keywordAnalysisClient;

    public void execute(String jobId) {
        KeywordAnalysisWork work = transactionService.start(jobId);
        if (work == null) {
            return;
        }

        try {
            List<KeywordAnalysisResult> results = keywordAnalysisClient.analyze(work.request());
            transactionService.complete(jobId, results);
        } catch (KeywordAnalysisClientException exception) {
            transactionService.fail(jobId, exception.getReason());
        }
    }
}
