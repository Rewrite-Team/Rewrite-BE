package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class KeywordAnalysisKeywordRepositoryTest {

    @Autowired
    private KeywordAnalysisKeywordRepository repository;

    @Autowired
    private KeywordAnalysisRepository keywordAnalysisRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByKeywordAnalysisIdReturnsKeywordsOrderedByKeywordOrder() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.save(KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        repository.save(KeywordAnalysisKeyword.of("kak_2", keywordAnalysis, 2, "Spring", 88));
        repository.save(KeywordAnalysisKeyword.of("kak_1", keywordAnalysis, 1, "백엔드", 95));

        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByKeywordAnalysisIdOrderByKeywordOrderAsc("ka_1"))
                .extracting(
                        KeywordAnalysisKeyword::getId,
                        KeywordAnalysisKeyword::getKeywordOrder,
                        KeywordAnalysisKeyword::getKeyword,
                        KeywordAnalysisKeyword::getImportance
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kak_1", 1, "백엔드", 95),
                        org.assertj.core.groups.Tuple.tuple("kak_2", 2, "Spring", 88)
                );
    }
}
