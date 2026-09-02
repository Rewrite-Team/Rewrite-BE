package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class ReviewJobEventListener {

    private final LlmJobRepository llmJobRepository;
    private final FirstReviewJobWorker firstReviewJobWorker;
    private final ReReviewJobWorker reReviewJobWorker;

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
            }
        }
    }
}
