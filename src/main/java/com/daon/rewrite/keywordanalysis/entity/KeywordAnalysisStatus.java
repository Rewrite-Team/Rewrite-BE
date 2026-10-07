package com.daon.rewrite.keywordanalysis.entity;

/**
 * 자기소개서별 분석 결과의 상태다. Job이 아직 PENDING이어도 분석 리소스는 PROCESSING으로 저장한다.
 * 미시작 상태는 분석 행이 없는 경우 응답 계층에서 NOT_STARTED로 표현한다.
 */
public enum KeywordAnalysisStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
