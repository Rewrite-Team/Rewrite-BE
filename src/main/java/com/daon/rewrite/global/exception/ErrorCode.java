package com.daon.rewrite.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            "리소스를 찾을 수 없습니다."
    ),

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "입력값이 올바르지 않습니다."
    ),

    CONFLICT(
            HttpStatus.CONFLICT,
            "CONFLICT",
            "요청이 현재 리소스 상태와 충돌합니다."
    ),

    COVER_LETTER_NOT_DRAFT(
            HttpStatus.CONFLICT,
            "COVER_LETTER_NOT_DRAFT",
            "제출된 자기소개서의 원본 정보는 수정할 수 없습니다."
    ),

    LLM_JOB_ALREADY_RUNNING(
            HttpStatus.CONFLICT,
            "LLM_JOB_ALREADY_RUNNING",
            "이미 진행 중인 LLM 작업이 있습니다."
    ),

    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
