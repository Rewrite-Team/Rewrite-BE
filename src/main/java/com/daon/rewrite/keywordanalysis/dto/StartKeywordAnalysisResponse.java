package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.service.StartKeywordAnalysisResult;

public record StartKeywordAnalysisResponse(
        String jobId,
        String keywordAnalysisId,
        String coverLetterId,
        KeywordAnalysisStatus status
) {

    public static StartKeywordAnalysisResponse from(StartKeywordAnalysisResult result) {
        return new StartKeywordAnalysisResponse(
                result.job().getId(),
                result.keywordAnalysis().getId(),
                result.keywordAnalysis().getCoverLetter().getId(),
                result.keywordAnalysis().getStatus()
        );
    }
}
