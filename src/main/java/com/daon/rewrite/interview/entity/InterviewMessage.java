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
