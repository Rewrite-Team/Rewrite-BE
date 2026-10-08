package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.service.CoverLetterDetailResult;
import com.daon.rewrite.keywordanalysis.dto.LatestKeywordAnalysisResponse;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewVersionLabelResponseTest {

    @ParameterizedTest
    @CsvSource({"1, v0.1", "9, v0.9", "10, v0.10"})
    void listDetailAndKeywordResponsesKeepThePublicVersionLabel(long versionNumber, String expectedLabel) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        CoverLetter coverLetter = CoverLetter.create("cl_test", "owner", now);
        ReviewVersion reviewVersion = ReviewVersion.started("rv_test", coverLetter, versionNumber, null, null, now);
        coverLetter.completeReview(reviewVersion.getId(), now);
        var listResponse = ReviewVersionListItemResponse.from(new ReviewVersionSummary(reviewVersion, true, true));
        var detailResponse = CoverLetterDetailResponse.from(new CoverLetterDetailResult(
                coverLetter, new CoverLetterDetailResult.ReviewVersionResult(reviewVersion, true, true),
                null, List.of()
        ));
        KeywordAnalysis analysis = KeywordAnalysis.processing("ka_test", coverLetter, reviewVersion.getId(), now);
        var keywordResponse = LatestKeywordAnalysisResponse.from(LatestKeywordAnalysisResult.of(
                coverLetter, reviewVersion, analysis, null, List.of()
        ));

        assertThat(listResponse.version()).isEqualTo(expectedLabel);
        assertThat(detailResponse.reviewVersion().version()).isEqualTo(expectedLabel);
        assertThat(keywordResponse.sourceReviewVersion().version()).isEqualTo(expectedLabel);
    }
}
