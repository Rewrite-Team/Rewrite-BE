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

/**
 * 첨삭 최종 작성본을 기준으로 생성한 예상 질문이다. 질문 생성 트랜잭션에서 대응하는 thread도 함께 저장한다.
 * 추가 질문은 기존 마지막 다음 순서를 사용하고, 질문마다 실제 생성 기준 버전을 기록한다.
 * 최초 질문 본문은 여기서 관리하며 assistant 메시지로 중복 저장하지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "interview_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_questions_session_order",
                columnNames = {"interview_session_id", "question_order"}
        )
)
public class InterviewQuestion {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_session_id", nullable = false)
    private InterviewSession interviewSession;

    @Column(name = "source_review_version_id", nullable = false, length = 64)
    private String sourceReviewVersionId;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private InterviewQuestionType type;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    private InterviewQuestion(
            String id,
            InterviewSession interviewSession,
            String sourceReviewVersionId,
            int questionOrder,
            InterviewQuestionType type,
            String question
    ) {
        this.id = id;
        this.interviewSession = interviewSession;
        this.sourceReviewVersionId = sourceReviewVersionId;
        this.questionOrder = questionOrder;
        this.type = type;
        this.question = question;
    }

    public static InterviewQuestion create(
            String id,
            InterviewSession interviewSession,
            String sourceReviewVersionId,
            int questionOrder,
            InterviewQuestionType type,
            String question
    ) {
        return new InterviewQuestion(
                id,
                interviewSession,
                sourceReviewVersionId,
                questionOrder,
                type,
                question
        );
    }
}
