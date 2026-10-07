package com.daon.rewrite.reviewversion.client;

/**
 * 전체 자기소개서 문맥을 참고해 지정한 문항 하나의 리포트와 수정본을 생성하는 외부 호출 경계다.
 * 문항별 병렬 실행·재시도와 결과 저장은 Job 처리 계층이 담당한다.
 */
public interface ReviewClient {

    ReviewResult reviewQuestion(ReviewRequest request, String targetQuestionId);
}
