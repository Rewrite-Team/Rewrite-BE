package com.daon.rewrite.interview.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InterviewSessionRepositoryTest {

    @Autowired
    private InterviewSessionRepository repository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindQuestionGeneratingSessionRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );

        repository.save(interviewSession);
        entityManager.flush();
        entityManager.clear();

        InterviewSession found = repository.findByCoverLetterId("cl_1").orElseThrow();

        assertThat(found.getId()).isEqualTo("is_1");
        assertThat(found.getCoverLetter().getId()).isEqualTo("cl_1");
        assertThat(found.getInitialSourceReviewVersionId()).isEqualTo("rv_1");
        assertThat(found.getStatus()).isEqualTo(InterviewSessionStatus.QUESTION_GENERATING);
        assertThat(found.getCreatedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void failedSessionRestartsWithNewSourceVersion() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        interviewSession.fail();
        interviewSession = repository.saveAndFlush(interviewSession);

        interviewSession.restartQuestionGeneration("rv_2");
        entityManager.flush();
        entityManager.clear();

        InterviewSession found = repository.findByCoverLetterId("cl_1").orElseThrow();

        assertThat(found.getId()).isEqualTo("is_1");
        assertThat(found.getInitialSourceReviewVersionId()).isEqualTo("rv_2");
        assertThat(found.getStatus()).isEqualTo(InterviewSessionStatus.QUESTION_GENERATING);
        assertThat(found.getCreatedAt()).isEqualTo(now.plusSeconds(60));
    }
}
