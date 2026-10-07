package com.daon.rewrite.interview.client.feedback;

/**
 * 원본 질문과 대화 이력에서 최신 USER 답변의 피드백·점수·꼬리질문을 생성하는 외부 호출 경계다.
 * 검증된 전체 결과를 반환하며 화면용 delta 전송과 assistant 메시지 저장은 Job 계층이 담당한다.
 */
public interface InterviewMessageFeedbackClient {

    InterviewMessageFeedbackResult generate(InterviewMessageFeedbackRequest request);
}
