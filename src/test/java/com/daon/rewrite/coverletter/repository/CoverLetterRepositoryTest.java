package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CoverLetterRepositoryTest {

    @Autowired
    private CoverLetterRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindDraftRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        CoverLetter draft = CoverLetter.draft("cl_fixed", "user_1", now);

        repository.save(draft);
        entityManager.flush();
        entityManager.clear();

        CoverLetter found = repository.findById("cl_fixed").orElseThrow();

        assertThat(found.getId()).isEqualTo("cl_fixed");
        assertThat(found.getOwnerId()).isEqualTo("user_1");
        assertThat(found.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(found.getCreatedAt()).isEqualTo(now);
        assertThat(found.getUpdatedAt()).isEqualTo(now);
        assertThat(found.getTitle()).isNull();
        assertThat(found.getCompanyName()).isNull();
        assertThat(found.getPositionTitle()).isNull();
        assertThat(found.getJobPostingUrl()).isNull();
        assertThat(found.getPreferences()).isNull();
        assertThat(found.getSubmittedAt()).isNull();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getLatestReviewVersionId()).isNull();
    }
}
