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

/** USER 답변과 피드백 Job이 함께 커밋된 뒤 해당 Job의 Worker를 비동기로 시작한다. */
@Component
@RequiredArgsConstructor
class InterviewMessageFeedbackJobEventListener {

    private final LlmJobRepository llmJobRepository;
    private final InterviewMessageFeedbackJobWorker worker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LlmJobCreatedEvent event) {
        llmJobRepository.findById(event.jobId()).ifPresent(this::dispatch);
    }

    private void dispatch(LlmJob job) {
        if (job.getType() == LlmJobType.INTERVIEW_MESSAGE_FEEDBACK) {
            worker.execute(job.getId());
        }
    }
}
