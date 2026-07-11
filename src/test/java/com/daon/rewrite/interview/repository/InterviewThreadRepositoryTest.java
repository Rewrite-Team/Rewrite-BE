package com.daon.rewrite.interview.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.entity.InterviewThreadStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class InterviewThreadRepositoryTest {

    @Autowired
    private InterviewThreadRepository repository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindByInterviewSessionRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-11T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.create(
                "iq_1",
                interviewSession,
                "rv_1",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        ));
        repository.save(InterviewThread.active(
                "it_1",
                interviewSession,
                question,
                now.plusSeconds(120)
        ));
        entityManager.flush();
        entityManager.clear();

        InterviewThread found = repository.findByInterviewSessionId("is_1").getFirst();

        assertThat(found.getId()).isEqualTo("it_1");
        assertThat(found.getInterviewSession().getId()).isEqualTo("is_1");
        assertThat(found.getInterviewQuestion().getId()).isEqualTo("iq_1");
        assertThat(found.getStatus()).isEqualTo(InterviewThreadStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void rejectSecondThreadForSameQuestion() {
        Instant now = Instant.parse("2026-07-11T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.create(
                "iq_1",
                interviewSession,
                "rv_1",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        ));
        repository.saveAndFlush(InterviewThread.active(
                "it_1",
                interviewSession,
                question,
                now.plusSeconds(120)
        ));

        assertThatThrownBy(() -> repository.saveAndFlush(InterviewThread.active(
                "it_2",
                interviewSession,
                question,
                now.plusSeconds(180)
        )))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
