package com.daon.rewrite.interview.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
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
class InterviewQuestionRepositoryTest {

    @Autowired
    private InterviewQuestionRepository repository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindQuestionsOrderedByQuestionOrderRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-10T11:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        repository.saveAll(List.of(
                InterviewQuestion.create(
                        "iq_2",
                        interviewSession,
                        "rv_1",
                        2,
                        InterviewQuestionType.TECHNICAL,
                        "트랜잭션 격리 수준을 설명해 주세요."
                ),
                InterviewQuestion.create(
                        "iq_1",
                        interviewSession,
                        "rv_1",
                        1,
                        InterviewQuestionType.COVER_LETTER_BASED,
                        "프로젝트에서 맡은 역할을 설명해 주세요."
                )
        ));
        entityManager.flush();
        entityManager.clear();

        List<InterviewQuestion> found = repository
                .findByInterviewSessionIdOrderByQuestionOrderAsc("is_1");

        assertThat(found)
                .extracting(
                        InterviewQuestion::getId,
                        question -> question.getInterviewSession().getId(),
                        InterviewQuestion::getSourceReviewVersionId,
                        InterviewQuestion::getQuestionOrder,
                        InterviewQuestion::getType,
                        InterviewQuestion::getQuestion
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_1",
                                "is_1",
                                "rv_1",
                                1,
                                InterviewQuestionType.COVER_LETTER_BASED,
                                "프로젝트에서 맡은 역할을 설명해 주세요."
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_2",
                                "is_1",
                                "rv_1",
                                2,
                                InterviewQuestionType.TECHNICAL,
                                "트랜잭션 격리 수준을 설명해 주세요."
                        )
                );
    }
}
