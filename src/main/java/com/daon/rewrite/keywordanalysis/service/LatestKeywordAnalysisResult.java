package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;

import java.util.List;

public record LatestKeywordAnalysisResult(
        String coverLetterId,
        KeywordAnalysis keywordAnalysis,
        List<KeywordAnalysisKeyword> keywords
) {

    public static LatestKeywordAnalysisResult empty(String coverLetterId) {
        return new LatestKeywordAnalysisResult(coverLetterId, null, List.of());
    }

    public static LatestKeywordAnalysisResult of(
            String coverLetterId,
            KeywordAnalysis keywordAnalysis,
            List<KeywordAnalysisKeyword> keywords
    ) {
        return new LatestKeywordAnalysisResult(coverLetterId, keywordAnalysis, keywords);
    }
}
