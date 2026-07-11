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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "interview_threads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_threads_question",
                columnNames = "interview_question_id"
        )
)
public class InterviewThread {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_session_id", nullable = false)
    private InterviewSession interviewSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_question_id", nullable = false)
    private InterviewQuestion interviewQuestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InterviewThreadStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private InterviewThread(
            String id,
            InterviewSession interviewSession,
            InterviewQuestion interviewQuestion,
            InterviewThreadStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.interviewSession = interviewSession;
        this.interviewQuestion = interviewQuestion;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static InterviewThread active(
            String id,
            InterviewSession interviewSession,
            InterviewQuestion interviewQuestion,
            Instant now
    ) {
        if (!interviewQuestion.getInterviewSession().getId().equals(interviewSession.getId())) {
            throw new IllegalArgumentException("면접 질문은 대화방과 같은 면접 세션에 속해야 합니다.");
        }
        return new InterviewThread(
                id,
                interviewSession,
                interviewQuestion,
                InterviewThreadStatus.ACTIVE,
                now
        );
    }
}
