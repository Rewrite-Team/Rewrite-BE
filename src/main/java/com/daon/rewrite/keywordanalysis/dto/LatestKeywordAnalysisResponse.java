package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;

import java.util.List;

public record LatestKeywordAnalysisResponse(
        String status,
        List<KeywordResponse> keywords
) {

    private static final String NOT_STARTED_STATUS = "NOT_STARTED";

    public static LatestKeywordAnalysisResponse from(LatestKeywordAnalysisResult result) {
        if (result.keywordAnalysis() == null) {
            return new LatestKeywordAnalysisResponse(NOT_STARTED_STATUS, List.of());
        }
        return new LatestKeywordAnalysisResponse(
                result.keywordAnalysis().getStatus().name(),
                result.keywords().stream()
                        .map(KeywordResponse::from)
                        .toList()
            );
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
