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

/**
 * 등록 step3에서 저장한 원본 문항·답변이다. WRITING의 미완성 입력을 보존하도록 내용 필드는 nullable이다.
 * 문항 목록 교체 시 ID와 순서를 새로 부여하며, 제출 이후에는 첨삭 입력의 기준 원본으로 유지한다.
 * 진행 중인 첨삭 결과는 ReviewJobQuestionResult, 확정 결과는 ReviewVersionQuestionResult가 보관한다.
 */
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

    @Column(name = "question", length = 300)
    private String question;

    @Column(name = "max_answer_length")
    private Integer maxAnswerLength;

    @Column(name = "original_answer", columnDefinition = "text")
    private String originalAnswer;

    private CoverLetterQuestion(
            String id,
            CoverLetter coverLetter,
            int questionOrder,
            String question,
            Integer maxAnswerLength,
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
            Integer maxAnswerLength,
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
