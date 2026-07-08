package com.daon.rewrite.keywordanalysis.client;

public record KeywordAnalysisAnswer(
        String questionId,
        int questionOrder,
        String question,
        String finalAnswer
) {
}
