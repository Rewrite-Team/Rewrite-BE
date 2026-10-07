package com.daon.rewrite.coverletter.entity;

/**
 * 목록·상세·사용자 SSE의 displayStatus로 그대로 전달하는 자기소개서 표시 상태다.
 * 최초·재첨삭 모두 같은 상태를 사용하며, REVIEW_FAILED에서 이전 성공 결과 존재 여부는 최신 성공 버전 ID로 구분한다.
 */
public enum CoverLetterStatus {
    WRITING,
    REVIEWING,
    REVIEWED,
    REVIEW_FAILED
}
