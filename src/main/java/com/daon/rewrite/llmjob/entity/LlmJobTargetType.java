package com.daon.rewrite.llmjob.entity;

/**
 * 소유권 확인과 동시 실행 배제의 기준이 되는 Job 대상의 종류다.
 * 현재는 첨삭·키워드·면접 작업 모두 COVER_LETTER와 자기소개서 ID를 사용한다.
 */
public enum LlmJobTargetType {
    COVER_LETTER
}
