package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewRequest;

/** 시작 트랜잭션에서 준비해 외부 호출 단계로 넘길 입력값이다. 영속 엔티티 대신 전체 문맥과 문항 값을 담는다. */
record ReviewWork(ReviewRequest request) {
}
