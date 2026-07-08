package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.reviewversion.service.FirstReviewJobWorker;
import com.daon.rewrite.reviewversion.service.ReReviewJobWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

@SpringBootTest
@ActiveProfiles("test")
class KeywordAnalysisJobEventIntegrationTest {

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private KeywordAnalysisJobWorker keywordAnalysisJobWorker;

    @MockitoBean
    private FirstReviewJobWorker firstReviewJobWorker;

    @MockitoBean
    private ReReviewJobWorker reReviewJobWorker;

    @AfterEach
    void cleanUp() {
        llmJobRepository.deleteAll();
    }

    @Test
    void keywordAnalysisJobEventSchedulesKeywordAnalysisWorkerAfterCommit() {
        Instant now = Instant.parse("2026-07-05T00:00:00Z");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            llmJobRepository.save(LlmJob.pendingKeywordAnalysis("job_1", "cl_1", now));
            eventPublisher.publishEvent(new LlmJobCreatedEvent("job_1"));
        });

        then(keywordAnalysisJobWorker).should(timeout(1000)).execute("job_1");
        then(firstReviewJobWorker).should(never()).execute("job_1");
        then(reReviewJobWorker).should(never()).execute("job_1");
    }
}
