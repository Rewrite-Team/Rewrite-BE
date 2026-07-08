package com.daon.rewrite.keywordanalysis.client;

import java.util.List;

public interface KeywordAnalysisClient {

    List<KeywordAnalysisResult> analyze(KeywordAnalysisRequest request);
}
