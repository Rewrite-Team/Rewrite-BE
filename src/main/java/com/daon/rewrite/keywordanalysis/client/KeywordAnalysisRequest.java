package com.daon.rewrite.keywordanalysis.client;

import java.util.List;

public record KeywordAnalysisRequest(
        String title,
        String companyName,
        String positionTitle,
        String preferences,
        List<KeywordAnalysisAnswer> answers
) {

    public KeywordAnalysisRequest {
        answers = List.copyOf(answers);
    }
}
