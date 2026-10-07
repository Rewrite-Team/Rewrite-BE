package com.daon.rewrite.reviewversion.client;

/**
 * 외부 호출 실패와 응답 변환·내용 검증 실패를 Job 계층이 구분할 수 있게 전달한다.
 * Job 처리 계층은 이 예외를 문항 재시도 대상으로 다루고 reason을 최종 Job 오류 코드 변환에 사용한다.
 */
public class ReviewClientException extends RuntimeException {

    private final Reason reason;

    private ReviewClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static ReviewClientException outputValidationFailed() {
        return new ReviewClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "첨삭 결과 구조가 올바르지 않습니다.",
                null
        );
    }

    public static ReviewClientException outputValidationFailed(Throwable cause) {
        return new ReviewClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "첨삭 결과를 변환할 수 없습니다.",
                cause
        );
    }

    public static ReviewClientException providerError(Throwable cause) {
        return new ReviewClientException(
                Reason.PROVIDER_ERROR,
                "첨삭 provider 호출에 실패했습니다.",
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
