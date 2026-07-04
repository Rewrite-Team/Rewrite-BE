package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record StartKeywordAnalysisResult(
        KeywordAnalysis keywordAnalysis,
        LlmJob job
) {
}
