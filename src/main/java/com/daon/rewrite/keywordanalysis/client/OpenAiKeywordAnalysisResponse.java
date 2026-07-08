package com.daon.rewrite.keywordanalysis.client;

import java.util.List;

record OpenAiKeywordAnalysisResponse(List<KeywordAnalysisResult> keywords) {
}
