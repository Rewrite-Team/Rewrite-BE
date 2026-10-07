package com.daon.rewrite.interview.job;

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
 * 질문 생성 Job의 요청 트랜잭션이 커밋된 뒤 초기·추가 질문 생성 Worker를 별도 스레드에서 시작한다.
 * 공통 Job 이벤트 중 질문 생성 두 종류만 처리하며, 답변 피드백은 전용 리스너가 담당한다.
 */
@Component
@RequiredArgsConstructor
class InterviewQuestionGenerationJobEventListener {

    private final LlmJobRepository llmJobRepository;
    private final InterviewQuestionGenerationJobWorker worker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LlmJobCreatedEvent event) {
        llmJobRepository.findById(event.jobId()).ifPresent(this::dispatch);
    }

    private void dispatch(LlmJob job) {
        if (job.getType().isInterviewQuestionGeneration()) {
            worker.execute(job.getId());
        }
    }
}
