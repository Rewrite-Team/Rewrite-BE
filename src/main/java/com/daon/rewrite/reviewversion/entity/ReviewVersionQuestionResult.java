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

/**
 * 전체 첨삭이 성공한 버전의 문항별 입력·AI 리포트·수정본·최종 작성본이다.
 * originalAnswer는 그 첨삭의 실제 입력이며, 재첨삭에서는 이전 성공 버전의 finalAnswer에 해당한다.
 * AI 수정본과 입력은 보존하고, 사용자의 후속 편집은 finalAnswer만 갱신한다.
 */
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

    /** 문항의 제출 원본을 originalAnswer로 삼아 결과를 만든다. */
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

    /**
     * Job에 고정된 입력 답변을 받아 확정 결과를 만든다.
     * 문항의 제출 원본을 다시 읽지 않아 재첨삭 결과에서도 실제 입력 기준을 유지한다.
     */
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

    // 최신 성공 버전 여부와 전체 입력 검증은 ReviewVersionCommandService가 담당하고 여기서는 최종본과 길이만 갱신한다.
    public void updateFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
        this.finalAnswerLength = countCodePoints(finalAnswer);
    }

    private static int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
