package com.daon.rewrite.reviewversion.entity;

import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "review_version_question_results",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_question_results_version_question",
                        columnNames = {"review_version_id", "question_id"}
                ),
                @UniqueConstraint(
                        name = "uk_review_question_results_version_order",
                        columnNames = {"review_version_id", "question_order"}
                )
        }
)
public class ReviewVersionQuestionResult {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_version_id", nullable = false)
    private ReviewVersion reviewVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private CoverLetterQuestion question;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question", nullable = false, length = 300)
    private String questionText;

    @Column(name = "max_answer_length", nullable = false)
    private int maxAnswerLength;

    @Column(name = "original_answer", nullable = false, columnDefinition = "text")
    private String originalAnswer;

    @Column(name = "original_answer_length", nullable = false)
    private int originalAnswerLength;

    @Column(name = "ai_report", nullable = false, columnDefinition = "text")
    private String aiReport;

    @Column(name = "rewritten_answer", nullable = false, columnDefinition = "text")
    private String rewrittenAnswer;

    @Column(name = "rewritten_answer_length", nullable = false)
    private int rewrittenAnswerLength;

    @Column(name = "final_answer", nullable = false, columnDefinition = "text")
    private String finalAnswer;

    @Column(name = "final_answer_length", nullable = false)
    private int finalAnswerLength;

    private ReviewVersionQuestionResult(
            String id,
            ReviewVersion reviewVersion,
            CoverLetterQuestion question,
            String originalAnswer,
            String aiReport,
            String rewrittenAnswer
    ) {
        this.id = id;
        this.reviewVersion = reviewVersion;
        this.question = question;
        this.questionOrder = question.getQuestionOrder();
        this.questionText = question.getQuestion();
        this.maxAnswerLength = question.getMaxAnswerLength();
        this.originalAnswer = originalAnswer;
        this.originalAnswerLength = countCodePoints(originalAnswer);
        this.aiReport = aiReport;
        this.rewrittenAnswer = rewrittenAnswer;
        this.rewrittenAnswerLength = countCodePoints(rewrittenAnswer);
        this.finalAnswer = rewrittenAnswer;
        this.finalAnswerLength = rewrittenAnswerLength;
    }

    public static ReviewVersionQuestionResult create(
            String id,
            ReviewVersion reviewVersion,
            CoverLetterQuestion question,
            String aiReport,
            String rewrittenAnswer
    ) {
        return new ReviewVersionQuestionResult(
                id,
                reviewVersion,
                question,
                question.getOriginalAnswer(),
                aiReport,
                rewrittenAnswer
        );
    }

    public static ReviewVersionQuestionResult createFromSnapshot(
            String id,
            ReviewVersion reviewVersion,
            CoverLetterQuestion question,
            String originalAnswer,
            String aiReport,
            String rewrittenAnswer
    ) {
        return new ReviewVersionQuestionResult(id, reviewVersion, question, originalAnswer, aiReport, rewrittenAnswer);
    }

    public void updateFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
        this.finalAnswerLength = countCodePoints(finalAnswer);
    }

    private static int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
