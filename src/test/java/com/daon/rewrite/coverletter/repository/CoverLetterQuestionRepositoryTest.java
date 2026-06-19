package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CoverLetterQuestionRepositoryTest {

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindByCoverLetterIdOrdersByQuestionOrder() {
        CoverLetter coverLetter = coverLetterRepository.save(
                CoverLetter.draft("cl_questions", "user_1", Instant.parse("2026-06-20T01:00:00Z"))
        );
        questionRepository.saveAll(List.of(
                CoverLetterQuestion.create("clq_2", coverLetter, 2, "두 번째 질문", 1500, "두 번째 답변"),
                CoverLetterQuestion.create("clq_1", coverLetter, 1, "첫 번째 질문", 1000, "첫 번째 답변")
        ));
        entityManager.flush();
        entityManager.clear();

        List<CoverLetterQuestion> questions = questionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc("cl_questions");

        assertThat(questions).extracting(CoverLetterQuestion::getId)
                .containsExactly("clq_1", "clq_2");
        assertThat(questions).extracting(CoverLetterQuestion::getQuestion)
                .containsExactly("첫 번째 질문", "두 번째 질문");
        assertThat(questions).extracting(CoverLetterQuestion::getMaxAnswerLength)
                .containsExactly(1000, 1500);
        assertThat(questions).extracting(CoverLetterQuestion::getOriginalAnswer)
                .containsExactly("첫 번째 답변", "두 번째 답변");
    }

    @Test
    void deleteByCoverLetterRemovesOnlyThatCoverLetterQuestions() {
        CoverLetter target = coverLetterRepository.save(
                CoverLetter.draft("cl_target", "user_1", Instant.parse("2026-06-20T01:00:00Z"))
        );
        CoverLetter other = coverLetterRepository.save(
                CoverLetter.draft("cl_other", "user_1", Instant.parse("2026-06-20T01:00:00Z"))
        );
        questionRepository.saveAll(List.of(
                CoverLetterQuestion.create("clq_target", target, 1, "대상 질문", 1000, "대상 답변"),
                CoverLetterQuestion.create("clq_other", other, 1, "다른 질문", 1000, "다른 답변")
        ));
        entityManager.flush();

        questionRepository.deleteByCoverLetter(target);
        entityManager.flush();
        entityManager.clear();

        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc("cl_target")).isEmpty();
        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc("cl_other"))
                .extracting(CoverLetterQuestion::getId)
                .containsExactly("clq_other");
    }
}
