package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LatestKeywordAnalysisResponseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void missingAnalysisIsNotStartedOnlyInTheResponse() {
        CoverLetter coverLetter = CoverLetter.create("cl_test", "owner", NOW);
        LatestKeywordAnalysisResult result = LatestKeywordAnalysisResult.empty(coverLetter);

        LatestKeywordAnalysisResponse response = LatestKeywordAnalysisResponse.from(result);
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(result.keywordAnalysis()).isNull();
        assertThat(response.status()).isEqualTo(LatestKeywordAnalysisStatus.NOT_STARTED);
        assertThat(json.path("status").asString()).isEqualTo("NOT_STARTED");
        assertThat(json.has("sourceReviewVersion")).isTrue();
        assertThat(json.path("sourceReviewVersion").isNull()).isTrue();
        assertThat(json.has("jobId")).isTrue();
        assertThat(json.path("jobId").isNull()).isTrue();
        assertThat(json.path("keywords").isEmpty()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(KeywordAnalysisStatus.class)
    void analysisStateKeepsItsPublicString(KeywordAnalysisStatus status) {
        CoverLetter coverLetter = CoverLetter.create("cl_test", "owner", NOW);
        ReviewVersion version = ReviewVersion.started("rv_test", coverLetter, 1, null, null, NOW);
        KeywordAnalysis analysis = KeywordAnalysis.processing("ka_test", coverLetter, version.getId(), NOW);
        switch (status) {
            case PROCESSING -> { }
            case COMPLETED -> analysis.complete(NOW);
            case FAILED -> analysis.fail(NOW);
        }

        LatestKeywordAnalysisResponse response = LatestKeywordAnalysisResponse.from(LatestKeywordAnalysisResult.of(
                coverLetter, version, analysis, null, List.of()
        ));

        assertThat(response.status()).isEqualTo(LatestKeywordAnalysisStatus.from(status));
        assertThat(objectMapper.valueToTree(response).path("status").asString()).isEqualTo(status.name());
        assertThat(analysis.getStatus()).isEqualTo(status);
    }
}
