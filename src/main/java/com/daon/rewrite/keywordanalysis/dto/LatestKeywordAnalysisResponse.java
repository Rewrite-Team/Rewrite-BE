package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;

import java.util.List;

public record LatestKeywordAnalysisResponse(
        CoverLetterResponse coverLetter,
        ReviewVersionResponse sourceReviewVersion,
        String status,
        String jobId,
        List<KeywordResponse> keywords
) {

    private static final String NOT_STARTED_STATUS = "NOT_STARTED";

    public static LatestKeywordAnalysisResponse from(LatestKeywordAnalysisResult result) {
        if (result.keywordAnalysis() == null) {
            return new LatestKeywordAnalysisResponse(
                    CoverLetterResponse.from(result.coverLetter()),
                    null,
                    NOT_STARTED_STATUS,
                    null,
                    List.of()
            );
        }
        return new LatestKeywordAnalysisResponse(
                CoverLetterResponse.from(result.coverLetter()),
                new ReviewVersionResponse(
                        result.sourceReviewVersion().getId(),
                        result.sourceReviewVersion().getVersion()
                ),
                result.keywordAnalysis().getStatus().name(),
                result.job() == null ? null : result.job().getId(),
                result.keywords().stream()
                        .map(KeywordResponse::from)
                        .toList()
        );
    }

    public record CoverLetterResponse(
            String id,
            String title,
            String companyName,
            String positionTitle
    ) {
        private static CoverLetterResponse from(CoverLetter coverLetter) {
            return new CoverLetterResponse(
                    coverLetter.getId(),
                    coverLetter.getTitle(),
                    coverLetter.getCompanyName(),
                    coverLetter.getPositionTitle()
            );
        }
    }

    public record ReviewVersionResponse(String id, String version) {
    }

    private record KeywordResponse(
            String keyword,
            int importance
    ) {

        private static KeywordResponse from(KeywordAnalysisKeyword keyword) {
            return new KeywordResponse(keyword.getKeyword(), keyword.getImportance());
        }
    }
}
