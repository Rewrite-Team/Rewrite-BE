package com.daon.rewrite.llmjob.service;

/**
 * Job과 관련 도메인 데이터를 저장하는 트랜잭션 안에서 발행하는 실행 시작 알림이다.
 * 도메인 리스너는 커밋 후 비동기로 Job을 조회하고 종류에 맞는 worker를 실행하며, 롤백된 생성 요청은 실행하지 않는다.
 * 애플리케이션 메모리의 이벤트이므로 프로세스 종료 후 재전달을 보장하지 않는다.
 */
public record LlmJobCreatedEvent(String jobId) {
}
