package com.daon.rewrite.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 질문별 대화방에 저장하는 USER 답변 또는 완료된 ASSISTANT 피드백이다.
 * ASSISTANT의 공개 content는 피드백과 꼬리질문을 연결한 문장이고, 구조화 피드백·별도 꼬리질문은 내부 필드로 보관한다.
 * USER는 답변만 보관해 점수가 null이며, ASSISTANT는 피드백 구조와 1~100점의 점수를 함께 가진다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "interview_messages")
public class InterviewMessage {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private InterviewThread thread;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private InterviewMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "feedback_summary", columnDefinition = "text")
    private String feedbackSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_strengths_json", columnDefinition = "json")
    private List<String> feedbackStrengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_improvements_json", columnDefinition = "json")
    private List<String> feedbackImprovements;

    @Column(name = "score")
    private Integer score;

    @Column(name = "follow_up_question", columnDefinition = "text")
    private String followUpQuestion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private InterviewMessage(
            String id,
            InterviewThread thread,
            InterviewMessageRole role,
            String content,
            String feedbackSummary,
            List<String> feedbackStrengths,
            List<String> feedbackImprovements,
            Integer score,
            String followUpQuestion,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.thread = Objects.requireNonNull(thread);
        this.role = Objects.requireNonNull(role);
        this.content = Objects.requireNonNull(content);
        this.feedbackSummary = feedbackSummary;
        this.feedbackStrengths = copyOfNullable(feedbackStrengths);
        this.feedbackImprovements = copyOfNullable(feedbackImprovements);
        this.score = score;
        this.followUpQuestion = followUpQuestion;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static InterviewMessage userAnswer(
            String id,
            InterviewThread thread,
            String content,
            Instant now
    ) {
        return new InterviewMessage(
                id,
                thread,
                InterviewMessageRole.USER,
                content,
                null,
                null,
                null,
                null,
                null,
                now
        );
    }

    /**
     * 생성이 완료된 피드백 한 개를 저장할 메시지로 구성한다. 꼬리질문도 같은 메시지에 포함한다.
     * LLM 클라이언트의 출력 검증 이후에도 필수 피드백 구조와 점수 범위를 엔티티 경계에서 확인한다.
     */
    public static InterviewMessage assistantFeedback(
            String id,
            InterviewThread thread,
            String content,
            String feedbackSummary,
            List<String> feedbackStrengths,
            List<String> feedbackImprovements,
            int score,
            String followUpQuestion,
            Instant now
    ) {
        Objects.requireNonNull(feedbackSummary);
        Objects.requireNonNull(feedbackStrengths);
        Objects.requireNonNull(feedbackImprovements);
        Objects.requireNonNull(followUpQuestion);
        if (score < 1 || score > 100) {
            throw new IllegalArgumentException("면접 점수는 1에서 100 사이여야 합니다.");
        }
        return new InterviewMessage(
                id,
                thread,
                InterviewMessageRole.ASSISTANT,
                content,
                feedbackSummary,
                feedbackStrengths,
                feedbackImprovements,
                score,
                followUpQuestion,
                now
        );
    }

    private static List<String> copyOfNullable(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }
}
