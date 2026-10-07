package com.daon.rewrite.keywordanalysis.job;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 분석 리소스와 Job 생성이 커밋된 뒤 키워드 분석 Worker를 비동기로 시작한다.
 * 공통 Job 생성 이벤트 중 KEYWORD_ANALYSIS만 처리하며, DB 상태 변경은 Worker의 트랜잭션 서비스가 담당한다.
 */
@Component
@RequiredArgsConstructor
class KeywordAnalysisJobEventListener {

    private final LlmJobRepository llmJobRepository;
    private final KeywordAnalysisJobWorker worker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LlmJobCreatedEvent event) {
        llmJobRepository.findById(event.jobId()).ifPresent(this::dispatch);
    }

    private void dispatch(LlmJob job) {
        if (job.getType() == LlmJobType.KEYWORD_ANALYSIS) {
            worker.execute(job.getId());
        }
    }
}
