package com.daon.rewrite.reviewversion.entity;

import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.llmjob.entity.LlmJob;
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
        name = "review_job_question_results",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_job_question_results_job_question",
                        columnNames = {"llm_job_id", "question_id"}
                ),
                @UniqueConstraint(
                        name = "uk_review_job_question_results_job_order",
                        columnNames = {"llm_job_id", "question_order"}
                )
        }
)
public class ReviewJobQuestionResult {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "llm_job_id", nullable = false)
    private LlmJob llmJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private CoverLetterQuestion question;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question", nullable = false, length = 300)
    private String questionText;

    @Column(name = "max_answer_length", nullable = false)
    private int maxAnswerLength;

    @Column(name = "input_answer", nullable = false, columnDefinition = "text")
    private String inputAnswer;

    @Column(name = "input_answer_length", nullable = false)
    private int inputAnswerLength;

    @Column(name = "ai_report", columnDefinition = "text")
    private String aiReport;

    @Column(name = "rewritten_answer", columnDefinition = "text")
    private String rewrittenAnswer;

    @Column(name = "rewritten_answer_length")
    private Integer rewrittenAnswerLength;

    @Column(name = "final_answer", columnDefinition = "text")
    private String finalAnswer;

    @Column(name = "final_answer_length")
    private Integer finalAnswerLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewJobQuestionResultStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    private ReviewJobQuestionResult(
            String id,
            LlmJob llmJob,
            CoverLetterQuestion question,
            String inputAnswer
    ) {
        this.id = id;
        this.llmJob = llmJob;
        this.question = question;
        this.questionOrder = question.getQuestionOrder();
        this.questionText = question.getQuestion();
        this.maxAnswerLength = question.getMaxAnswerLength();
        this.inputAnswer = inputAnswer;
        this.inputAnswerLength = countCodePoints(inputAnswer);
        this.status = ReviewJobQuestionResultStatus.PROCESSING;
    }

    public static ReviewJobQuestionResult processing(
            String id,
            LlmJob llmJob,
            CoverLetterQuestion question,
            String inputAnswer
    ) {
        return new ReviewJobQuestionResult(id, llmJob, question, inputAnswer);
    }

    public void complete(String aiReport, String rewrittenAnswer, Instant completedAt) {
        if (status != ReviewJobQuestionResultStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 문항 결과만 완료할 수 있습니다.");
        }
        this.aiReport = aiReport;
        this.rewrittenAnswer = rewrittenAnswer;
        this.rewrittenAnswerLength = countCodePoints(rewrittenAnswer);
        this.finalAnswer = rewrittenAnswer;
        this.finalAnswerLength = rewrittenAnswerLength;
        this.status = ReviewJobQuestionResultStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void fail(Instant completedAt) {
        if (status != ReviewJobQuestionResultStatus.PROCESSING) {
            return;
        }
        this.status = ReviewJobQuestionResultStatus.FAILED;
        this.completedAt = completedAt;
    }

    private static int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
