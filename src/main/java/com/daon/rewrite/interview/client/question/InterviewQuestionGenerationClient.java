package com.daon.rewrite.interview.client.question;

import java.util.List;

/**
 * 전달받은 자기소개서 최종 작성본과 기존 질문을 바탕으로 면접 질문을 생성하는 외부 호출 경계다.
 * 생성 기준 버전·요청 개수 선택과 질문·thread 저장, Job 상태 변경은 서비스·Job 계층이 담당한다.
 */
public interface InterviewQuestionGenerationClient {

    List<InterviewQuestionGenerationResult> generate(InterviewQuestionGenerationRequest request);
}
