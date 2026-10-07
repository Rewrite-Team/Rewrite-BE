package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제출·재첨삭 요청에서 발행한 공통 Job 생성 이벤트를 첨삭 Worker에 연결한다.
 * Job·버전 생성이 커밋된 뒤 실행되며, 작업 중 DB 변경은 Worker가 호출하는 트랜잭션 서비스가 담당한다.
 */
@Component
@RequiredArgsConstructor
class ReviewJobEventListener {

    private final LlmJobRepository llmJobRepository;
    private final FirstReviewJobWorker firstReviewJobWorker;
    private final ReReviewJobWorker reReviewJobWorker;

    // 요청 트랜잭션이 정상 커밋된 후 별도 스레드에서 실행하므로 LLM 응답이 HTTP 요청을 지연시키지 않는다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LlmJobCreatedEvent event) {
        llmJobRepository.findById(event.jobId()).ifPresent(this::dispatch);
    }

    private void dispatch(LlmJob job) {
        switch (job.getType()) {
            case COVER_LETTER_REVIEW -> firstReviewJobWorker.execute(job.getId());
            case COVER_LETTER_RE_REVIEW -> reReviewJobWorker.execute(job.getId());
            default -> {
                // 키워드 분석·면접 Job은 각 도메인의 전용 리스너가 처리한다.
            }
        }
    }
}
