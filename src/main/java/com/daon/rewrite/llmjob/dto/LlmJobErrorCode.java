package com.daon.rewrite.llmjob.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** HTTP 오류와 구분하는 공개 Job 오류 코드다. 내부 실패 분류는 응답 경계에서 정규화한다. */
@Schema(enumAsRef = true)
public enum LlmJobErrorCode {
    LLM_PROVIDER_ERROR,
    LLM_CONTEXT_LENGTH_EXCEEDED,
    LLM_CONTENT_FILTERED;

    public static LlmJobErrorCode fromInternalCode(String errorCode) {
        // 화면에서 구별하는 두 오류 외의 내부 실패 분류는 공통 provider 오류 코드로 공개한다.
        return switch (errorCode) {
            case "LLM_CONTEXT_LENGTH_EXCEEDED" -> LLM_CONTEXT_LENGTH_EXCEEDED;
            case "LLM_CONTENT_FILTERED" -> LLM_CONTENT_FILTERED;
            default -> LLM_PROVIDER_ERROR;
        };
    }
}
