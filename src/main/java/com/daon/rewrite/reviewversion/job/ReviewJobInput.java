package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewRequest;

import java.util.Set;

/**
 * 시작 트랜잭션에서 준비해 외부 호출 단계로 넘길 입력값이다. 영속 엔티티 대신 전체 문맥과 문항 값을 담는다.
 * request는 재사용 문항을 포함한 전체 문맥이고, pendingQuestionIds는 이번 실행에서 LLM을 호출할 문항만 가리킨다.
 */
record ReviewJobInput(ReviewRequest request, Set<String> pendingQuestionIds) {

    ReviewJobInput {
        pendingQuestionIds = Set.copyOf(pendingQuestionIds);
    }
}
