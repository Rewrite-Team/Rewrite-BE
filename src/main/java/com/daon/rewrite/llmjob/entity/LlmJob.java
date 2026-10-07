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

/**
 * 첨삭·키워드·면접 비동기 작업의 상태, 진행률, 재시도 정보와 입력·결과 참조를 기록한다.
 * 실제 LLM 호출과 결과 저장은 도메인별 worker·트랜잭션 서비스가 담당한다.
 * 완료·실패 전환의 선행 상태와 관련 도메인 검증도 호출자가 수행하며, 이 엔티티의 변경은 호출자 트랜잭션에 반영된다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "llm_jobs")
public class LlmJob {

    private static final int DEFAULT_ATTEMPT = 1;
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final String PENDING_MESSAGE = "처리를 기다리고 있습니다.";
    private static final String CANCELED_MESSAGE = "작업이 취소되었습니다.";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // 실행할 도메인 작업을 구분한다. 이벤트 리스너는 이 값에 맞는 worker를 선택한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private LlmJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LlmJobStatus status;

    // 소유권 확인과 동시 실행 배제의 기준이 되는 대상이다. 현재 모든 Job은 자기소개서를 대상으로 한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private LlmJobTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    // 재첨삭 요청의 선택적 추가 요구사항이다.
    @Column(name = "request_instruction", length = 1000)
    private String requestInstruction;

    // Job 생성 시 선택한 입력을 식별한다. 재첨삭·질문 생성은 기준 첨삭 버전, 피드백은 USER 메시지를 가리킨다.
    @Enumerated(EnumType.STRING)
    @Column(name = "request_ref_type", length = 40)
    private LlmJobRequestRefType requestRefType;

    @Column(name = "request_ref_id", length = 64)
    private String requestRefId;

    @Column(name = "progress_current", nullable = false)
    private int progressCurrent;

    // 작업별 진행 단위의 총수다. 첨삭은 문항 수, 초기 면접 질문은 5, 나머지 단일 작업은 1을 사용한다.
    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "progress_message")
    private String progressMessage;

    // 최초 시도를 1로 세는 내부 기록이다. 실제 재호출은 작업 실행 계층이 담당한다.
    @Column(name = "attempt", nullable = false)
    private int attempt;

    // 최초 시도와 재시도를 포함한 시도 기록의 상한이며 기본값은 2다.
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    // 완료 후 조회할 도메인 결과를 식별하며, 결과 본문은 각 도메인 테이블에 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "result_ref_type", length = 40)
    private LlmJobResultRefType resultRefType;

    @Column(name = "result_ref_id", length = 64)
    private String resultRefId;

    // 내부 실패 분류다. 공개 응답에서는 허용된 Job 오류 코드로 매핑한다.
    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 성공뿐 아니라 최종 실패·취소 시각도 이 필드에 기록한다.
    @Column(name = "completed_at")
    private Instant completedAt;

    private LlmJob(
            String id,
            LlmJobType type,
            LlmJobStatus status,
            LlmJobTargetType targetType,
            String targetId,
            String requestInstruction,
            LlmJobRequestRefType requestRefType,
            String requestRefId,
            int progressTotal,
            Instant createdAt
    ) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestInstruction = requestInstruction;
        this.requestRefType = requestRefType;
        this.requestRefId = requestRefId;
        this.progressCurrent = 0;
        this.progressTotal = progressTotal;
        this.progressMessage = PENDING_MESSAGE;
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
                null,
                null,
                progressTotal,
                now
        );
    }

    public static LlmJob pendingReReview(
            String id,
            String coverLetterId,
            String requestInstruction,
            String sourceReviewVersionId,
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
                LlmJobRequestRefType.REVIEW_VERSION,
                sourceReviewVersionId,
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
                null,
                null,
                1,
                now
        );
    }

    public static LlmJob pendingInitialInterviewQuestionGeneration(
            String id,
            String coverLetterId,
            String sourceReviewVersionId,
            Instant now
    ) {
        return new LlmJob(
                id,
                LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                LlmJobRequestRefType.REVIEW_VERSION,
                sourceReviewVersionId,
                5,
                now
        );
    }

    public static LlmJob pendingAdditionalInterviewQuestionGeneration(
            String id,
            String coverLetterId,
            String sourceReviewVersionId,
            Instant now
    ) {
        return new LlmJob(
                id,
                LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                LlmJobRequestRefType.REVIEW_VERSION,
                sourceReviewVersionId,
                1,
                now
        );
    }

    public static LlmJob pendingInterviewMessageFeedback(
            String id,
            String coverLetterId,
            String userMessageId,
            Instant now
    ) {
        return new LlmJob(
                id,
                LlmJobType.INTERVIEW_MESSAGE_FEEDBACK,
                LlmJobStatus.PENDING,
                LlmJobTargetType.COVER_LETTER,
                coverLetterId,
                null,
                LlmJobRequestRefType.INTERVIEW_MESSAGE,
                userMessageId,
                1,
                now
        );
    }

    /**
     * 대기 중인 Job을 PROCESSING으로 전환해 worker의 중복 시작을 거부한다.
     * 입력 준비와 관련 도메인 상태 확인을 마친 트랜잭션 서비스가 호출한다.
     */
    public void startProcessing(String progressMessage) {
        if (this.status != LlmJobStatus.PENDING) {
            throw new IllegalStateException("PENDING Job만 처리를 시작할 수 있습니다.");
        }
        this.status = LlmJobStatus.PROCESSING;
        this.progressMessage = progressMessage;
    }

    /**
     * 결과 리소스를 저장한 호출자가 완료 진행률·결과 참조·종료 시각을 함께 확정한다.
     * 진행률은 작업별 완료 단위에 맞춰 호출자가 전달한다.
     */
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

    /**
     * 처리 중인 Job에서 완료 단위를 하나 늘린다. 첨삭 문항 결과 저장 후 같은 트랜잭션에서 호출한다.
     * 전체 단위를 초과하거나 처리 중이 아닌 Job의 진행률 변경은 거부한다.
     */
    public void advanceProgress(String progressMessage) {
        if (status != LlmJobStatus.PROCESSING || progressCurrent >= progressTotal) {
            throw new IllegalStateException("처리 중인 Job의 진행률만 증가시킬 수 있습니다.");
        }
        this.progressCurrent++;
        this.progressMessage = progressMessage;
    }

    /**
     * PROCESSING과 기존 진행률을 유지하면서 시도 기록을 maxAttempts까지 증가시킨다.
     * 첨삭 문항별 재시도에서 호출되며, 여러 문항의 호출 횟수를 합산하는 카운터로 사용하지 않는다.
     */
    public void markRetried() {
        if (status != LlmJobStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 Job만 재시도할 수 있습니다.");
        }
        if (attempt < maxAttempts) {
            attempt++;
        }
    }

    /**
     * 최종 실패 시점의 진행률·오류·종료 시각을 기록한다.
     * 이미 성공한 부분 결과의 보존과 관련 도메인의 실패 전환은 호출자가 처리한다.
     */
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

    /**
     * 대기·진행 중인 Job만 취소하고, 이미 종료된 Job은 그대로 둔다.
     * 자기소개서 삭제 등 취소에 수반되는 도메인 변경은 호출자가 담당한다.
     */
    public void cancel(Instant completedAt) {
        if (status != LlmJobStatus.PENDING && status != LlmJobStatus.PROCESSING) {
            return;
        }
        this.status = LlmJobStatus.CANCELED;
        this.progressMessage = CANCELED_MESSAGE;
        this.completedAt = completedAt;
    }
}
