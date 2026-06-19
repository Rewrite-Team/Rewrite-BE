package com.daon.rewrite.coverletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cover_letter_questions")
public class CoverLetterQuestion {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question", nullable = false, length = 300)
    private String question;

    @Column(name = "max_answer_length", nullable = false)
    private int maxAnswerLength;

    @Column(name = "original_answer", nullable = false, columnDefinition = "text")
    private String originalAnswer;

    private CoverLetterQuestion(
            String id,
            CoverLetter coverLetter,
            int questionOrder,
            String question,
            int maxAnswerLength,
            String originalAnswer
    ) {
        this.id = id;
        this.coverLetter = coverLetter;
        this.questionOrder = questionOrder;
        this.question = question;
        this.maxAnswerLength = maxAnswerLength;
        this.originalAnswer = originalAnswer;
    }

    public static CoverLetterQuestion create(
            String id,
            CoverLetter coverLetter,
            int questionOrder,
            String question,
            int maxAnswerLength,
            String originalAnswer
    ) {
        return new CoverLetterQuestion(
                id,
                coverLetter,
                questionOrder,
                question,
                maxAnswerLength,
                originalAnswer
        );
    }
}
