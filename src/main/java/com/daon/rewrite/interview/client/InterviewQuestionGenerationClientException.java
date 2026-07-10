package com.daon.rewrite.interview.client;

public class InterviewQuestionGenerationClientException extends RuntimeException {

    private final Reason reason;

    private InterviewQuestionGenerationClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static InterviewQuestionGenerationClientException outputValidationFailed() {
        return new InterviewQuestionGenerationClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "면접 질문 생성 결과 구조가 올바르지 않습니다.",
                null
        );
    }

    public static InterviewQuestionGenerationClientException outputValidationFailed(Throwable cause) {
        return new InterviewQuestionGenerationClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "면접 질문 생성 결과를 변환할 수 없습니다.",
                cause
        );
    }

    public static InterviewQuestionGenerationClientException providerError(Throwable cause) {
        return new InterviewQuestionGenerationClientException(
                Reason.PROVIDER_ERROR,
                "면접 질문 생성 provider 호출에 실패했습니다.",
                cause
        );
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        PROVIDER_ERROR,
        OUTPUT_VALIDATION_FAILED
    }
}
