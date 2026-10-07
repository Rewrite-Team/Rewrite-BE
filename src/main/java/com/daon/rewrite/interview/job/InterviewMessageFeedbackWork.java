package com.daon.rewrite.interview.job;

import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackRequest;

/** 시작 트랜잭션에서 읽은 원본 면접 질문과 요청 USER까지의 대화 이력을 외부 생성 호출에 넘긴다. */
record InterviewMessageFeedbackWork(InterviewMessageFeedbackRequest request) {
}
