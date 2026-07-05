package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class KeywordAnalysisRepositoryTest {

    @Autowired
    private KeywordAnalysisRepository repository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindProcessingKeywordAnalysisRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        KeywordAnalysis keywordAnalysis = KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );

        repository.save(keywordAnalysis);
        entityManager.flush();
        entityManager.clear();

        KeywordAnalysis found = repository.findByCoverLetterId("cl_1").orElseThrow();

        assertThat(found.getId()).isEqualTo("ka_1");
        assertThat(found.getCoverLetter().getId()).isEqualTo("cl_1");
        assertThat(found.getSourceReviewVersionId()).isEqualTo("rv_1");
        assertThat(found.getStatus()).isEqualTo(KeywordAnalysisStatus.PROCESSING);
        assertThat(found.getCreatedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(found.getCompletedAt()).isNull();
    }

    @Test
    void saveAndFindFailedKeywordAnalysisRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        KeywordAnalysis keywordAnalysis = KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        keywordAnalysis.fail(now.plusSeconds(90));

        repository.save(keywordAnalysis);
        entityManager.flush();
        entityManager.clear();

        KeywordAnalysis found = repository.findByCoverLetterId("cl_1").orElseThrow();

        assertThat(found.getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
        assertThat(found.getCompletedAt()).isEqualTo(now.plusSeconds(90));
    }

}
