package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;

/**
 * 버전의 최신 시도 여부와 최신 성공 여부를 구분하는 목록용 결과다.
 * 새 시도가 실패하면 isLatest와 isLatestReviewed가 서로 다른 버전을 가리킬 수 있다.
 */
public record ReviewVersionSummary(
        ReviewVersion reviewVersion,
        boolean isLatest,
        boolean isLatestReviewed
) {
}
