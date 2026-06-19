package com.daon.rewrite.llmjob.repository;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LlmJobRepositoryTest {

    @Autowired
    private LlmJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindPendingReviewJobRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        LlmJob job = LlmJob.pendingReview("job_1", "cl_1", now, 3);

        repository.save(job);
        entityManager.flush();
        entityManager.clear();

        LlmJob found = repository.findById("job_1").orElseThrow();

        assertThat(found.getId()).isEqualTo("job_1");
        assertThat(found.getType()).isEqualTo(LlmJobType.COVER_LETTER_REVIEW);
        assertThat(found.getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(found.getTargetType()).isEqualTo(LlmJobTargetType.COVER_LETTER);
        assertThat(found.getTargetId()).isEqualTo("cl_1");
        assertThat(found.getProgressCurrent()).isZero();
        assertThat(found.getProgressTotal()).isEqualTo(3);
        assertThat(found.getProgressMessage()).isNull();
        assertThat(found.getAttempt()).isEqualTo(1);
        assertThat(found.getMaxAttempts()).isEqualTo(2);
        assertThat(found.getResultRefType()).isNull();
        assertThat(found.getResultRefId()).isNull();
        assertThat(found.getErrorCode()).isNull();
        assertThat(found.getErrorMessage()).isNull();
        assertThat(found.getCreatedAt()).isEqualTo(now);
        assertThat(found.getCompletedAt()).isNull();
    }

    @Test
    void saveAndFindCompletedJobRoundTripsResultReference() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        LlmJob job = LlmJob.pendingReview("job_done", "cl_1", now, 3);
        job.markCompleted(
                3,
                "첨삭이 완료되었습니다.",
                LlmJobResultRefType.REVIEW_VERSION,
                "rv_1",
                now.plusSeconds(60)
        );

        repository.save(job);
        entityManager.flush();
        entityManager.clear();

        LlmJob found = repository.findById("job_done").orElseThrow();

        assertThat(found.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(found.getProgressCurrent()).isEqualTo(3);
        assertThat(found.getProgressTotal()).isEqualTo(3);
        assertThat(found.getProgressMessage()).isEqualTo("첨삭이 완료되었습니다.");
        assertThat(found.getResultRefType()).isEqualTo(LlmJobResultRefType.REVIEW_VERSION);
        assertThat(found.getResultRefId()).isEqualTo("rv_1");
        assertThat(found.getCompletedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void saveAndFindFailedJobRoundTripsError() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        LlmJob job = LlmJob.pendingReview("job_failed", "cl_1", now, 3);
        job.markFailed(
                0,
                "LLM 첨삭에 실패했습니다.",
                "LLM_PROVIDER_ERROR",
                "LLM 응답 생성에 실패했습니다.",
                now.plusSeconds(60)
        );

        repository.save(job);
        entityManager.flush();
        entityManager.clear();

        LlmJob found = repository.findById("job_failed").orElseThrow();

        assertThat(found.getStatus()).isEqualTo(LlmJobStatus.FAILED);
        assertThat(found.getProgressCurrent()).isZero();
        assertThat(found.getProgressMessage()).isEqualTo("LLM 첨삭에 실패했습니다.");
        assertThat(found.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
        assertThat(found.getErrorMessage()).isEqualTo("LLM 응답 생성에 실패했습니다.");
        assertThat(found.getCompletedAt()).isEqualTo(now.plusSeconds(60));
    }
}
