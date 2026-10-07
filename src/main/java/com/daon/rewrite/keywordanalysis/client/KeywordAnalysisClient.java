package com.daon.rewrite.keywordanalysis.client;

import java.util.List;

/**
 * 전달받은 자기소개서 최종 작성본에서 키워드와 중요도를 생성하는 외부 호출 경계다.
 * 분석 기준 버전 선택·입력 조회·결과 저장과 Job 상태 변경은 서비스·Job 계층이 담당한다.
 */
public interface KeywordAnalysisClient {

    List<KeywordAnalysisResult> analyze(KeywordAnalysisRequest request);
}
