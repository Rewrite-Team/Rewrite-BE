package com.daon.rewrite.interview.job;

import com.daon.rewrite.interview.client.question.InterviewQuestionGenerationRequest;

/** 시작 트랜잭션에서 읽은 지원 정보·기준 버전 답변·기존 질문을 외부 생성 호출에 넘기는 입력값이다. */
record InterviewQuestionGenerationWork(InterviewQuestionGenerationRequest request) {
}
