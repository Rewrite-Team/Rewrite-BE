package com.daon.rewrite.llmjob.entity;

/**
 * 외부 LLM 호출 실패를 Job에 기록할 오류 코드·메시지로 분류한다.
 * 각 도메인 클라이언트의 실패 사유가 이 값으로 변환되며, 사용자에게 provider 원문 메시지를 노출하지 않는다.
 */
public enum LlmJobFailureKind {

    PROVIDER_ERROR("LLM_PROVIDER_ERROR", "LLM 응답 생성에 실패했습니다."),
    OUTPUT_VALIDATION_FAILED("LLM_OUTPUT_VALIDATION_FAILED", "LLM 출력 형식이 올바르지 않습니다.");

    private final String errorCode;
    private final String errorMessage;

    LlmJobFailureKind(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
