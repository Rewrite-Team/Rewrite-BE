package com.daon.rewrite.keywordanalysis.job;

import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisRequest;

/** 시작 트랜잭션에서 읽은 공통 문맥과 기준 버전의 답변을 외부 호출 단계로 넘기는 입력값이다. */
record KeywordAnalysisWork(KeywordAnalysisRequest request) {
}
