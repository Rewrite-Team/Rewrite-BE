package com.daon.rewrite.llmjob.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "llm_jobs")
public class LlmJob {

    private static final int DEFAULT_ATTEMPT = 1;
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private LlmJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LlmJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private LlmJobTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "request_instruction", length = 1000)
    private String requestInstruction;

    @Column(name = "progress_current", nullable = false)
    private int progressCurrent;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "progress_message")
    private String progressMessage;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_ref_type", length = 40)
    private LlmJobResultRefType resultRefType;

    @Column(name = "result_ref_id", length = 64)
    private String resultRefId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private LlmJob(
            String id,
            LlmJobType type,
            LlmJobStatus status,
            LlmJobTargetType targetType,
            String targetId,
            String requestInstruction,
            int progressTotal,
            Instant createdAt
    ) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestInstruction = requestInstruction;
        this.progressCurrent = 0;
        this.progressTotal = progressTotal;
        this.attempt = DEFAULT_ATTEMPT;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.createdAt = createdAt;
    }

    public static LlmJob pendingReview(String id, String coverLetterId, Instant now, int progressTotal) {
        return new LlmJob(
                id,
                LlmJobType.COVER_LETTER_REVIEW,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                progressTotal,
                now
        );
    }

    public static LlmJob pendingReReview(
            String id,
            String coverLetterId,
            String requestInstruction,
            Instant now,
            int progressTotal
    ) {
        return new LlmJob(
                id,
                LlmJobType.COVER_LETTER_RE_REVIEW,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                requestInstruction,
                progressTotal,
                now
        );
    }

    public static LlmJob pendingKeywordAnalysis(String id, String coverLetterId, Instant now) {
        return new LlmJob(
                id,
                LlmJobType.KEYWORD_ANALYSIS,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                1,
                now
        );
    }

    public static LlmJob pendingInterviewQuestionGeneration(String id, String coverLetterId, Instant now) {
        return new LlmJob(
                id,
                LlmJobType.INTERVIEW_QUESTION_GENERATION,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                5,
                now
        );
    }

    public static LlmJob pendingInterviewMessageFeedback(String id, String coverLetterId, Instant now) {
        return new LlmJob(
                id,
                LlmJobType.INTERVIEW_MESSAGE_FEEDBACK,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                1,
                now
        );
    }

    public void startProcessing(String progressMessage) {
        if (this.status != LlmJobStatus.PENDING) {
            throw new IllegalStateException("PENDING Job만 처리를 시작할 수 있습니다.");
        }
        this.status = LlmJobStatus.PROCESSING;
        this.progressMessage = progressMessage;
    }

    public void markCompleted(
            int progressCurrent,
            String progressMessage,
            LlmJobResultRefType resultRefType,
            String resultRefId,
            Instant completedAt
    ) {
        this.status = LlmJobStatus.COMPLETED;
        this.progressCurrent = progressCurrent;
        this.progressMessage = progressMessage;
        this.resultRefType = resultRefType;
        this.resultRefId = resultRefId;
        this.completedAt = completedAt;
    }

    public void markFailed(
            int progressCurrent,
            String progressMessage,
            String errorCode,
            String errorMessage,
            Instant completedAt
    ) {
        this.status = LlmJobStatus.FAILED;
        this.progressCurrent = progressCurrent;
        this.progressMessage = progressMessage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }
}
