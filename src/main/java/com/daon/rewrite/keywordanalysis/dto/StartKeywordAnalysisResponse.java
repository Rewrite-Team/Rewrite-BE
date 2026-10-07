package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.service.StartKeywordAnalysisResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** 분석 결과를 기다리지 않고 현재 분석 상태와 새로 생성하거나 재사용한 Job의 ID를 반환한다. */
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
