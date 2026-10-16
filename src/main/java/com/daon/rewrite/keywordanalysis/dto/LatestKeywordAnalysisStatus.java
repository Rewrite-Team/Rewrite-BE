package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 분석 행이 없는 미시작 상태를 포함하는 조회 응답 전용 상태다. */
@Schema(enumAsRef = true)
public enum LatestKeywordAnalysisStatus {
    NOT_STARTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public static LatestKeywordAnalysisStatus from(KeywordAnalysisStatus status) {
        return switch (status) {
            case PROCESSING -> PROCESSING;
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
        };
    }
}
