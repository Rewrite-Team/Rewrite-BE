package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제출·재첨삭 요청에서 발행한 공통 Job 생성 이벤트를 첨삭 Executor에 연결한다.
 * Job·버전 생성이 커밋된 뒤 실행되며, 키워드 분석·면접 Job은 Executor가 무시하고 각 도메인의 전용 리스너가 처리한다.
 */
@Component
@RequiredArgsConstructor
class ReviewJobEventListener {

    private final ReviewJobExecutor reviewJobExecutor;

    // 요청 트랜잭션이 정상 커밋된 후 별도 스레드에서 실행하므로 LLM 응답이 HTTP 요청을 지연시키지 않는다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LlmJobCreatedEvent event) {
        reviewJobExecutor.execute(event.jobId());
    }
}
