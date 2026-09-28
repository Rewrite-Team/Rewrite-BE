package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.service.StartKeywordAnalysisResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"status", "jobId"})
public record StartKeywordAnalysisResponse(
        KeywordAnalysisStatus status,
        String jobId
) {

    public static StartKeywordAnalysisResponse from(StartKeywordAnalysisResult result) {
        return new StartKeywordAnalysisResponse(
                result.keywordAnalysis().getStatus(),
                result.job().getId()
        );
    }
}
