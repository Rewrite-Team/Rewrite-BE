package com.daon.rewrite.reviewversion.client;

public class FirstReviewClientException extends RuntimeException {

    private final Reason reason;

    private FirstReviewClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    static FirstReviewClientException outputValidationFailed() {
        return new FirstReviewClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "최초 첨삭 결과 구조가 올바르지 않습니다.",
                null
        );
    }

    static FirstReviewClientException outputValidationFailed(Throwable cause) {
        return new FirstReviewClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "최초 첨삭 결과를 변환할 수 없습니다.",
                cause
        );
    }

    static FirstReviewClientException providerError(Throwable cause) {
        return new FirstReviewClientException(
                Reason.PROVIDER_ERROR,
                "최초 첨삭 provider 호출에 실패했습니다.",
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
