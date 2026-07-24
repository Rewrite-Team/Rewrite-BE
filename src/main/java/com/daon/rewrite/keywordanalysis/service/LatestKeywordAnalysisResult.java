package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;

import java.util.List;

public record LatestKeywordAnalysisResult(
        CoverLetter coverLetter,
        ReviewVersion sourceReviewVersion,
        KeywordAnalysis keywordAnalysis,
        LlmJob job,
        List<KeywordAnalysisKeyword> keywords
) {

    public static LatestKeywordAnalysisResult empty(CoverLetter coverLetter) {
        return new LatestKeywordAnalysisResult(coverLetter, null, null, null, List.of());
    }

    public static LatestKeywordAnalysisResult of(
            CoverLetter coverLetter,
            ReviewVersion sourceReviewVersion,
            KeywordAnalysis keywordAnalysis,
            LlmJob job,
            List<KeywordAnalysisKeyword> keywords
    ) {
        return new LatestKeywordAnalysisResult(
                coverLetter,
                sourceReviewVersion,
                keywordAnalysis,
                job,
                keywords
        );
    }
}
