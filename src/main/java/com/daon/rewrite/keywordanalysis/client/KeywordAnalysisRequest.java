package com.daon.rewrite.keywordanalysis.client;

import java.util.List;

/**
 * 분석 기준 버전의 문항별 finalAnswer와 지원 정보를 함께 전달하는 LLM 입력이다.
 * client는 서비스가 조회해 전달한 전체 문항을 사용하며 기준 버전을 직접 선택하거나 조회하지 않는다.
 */
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
