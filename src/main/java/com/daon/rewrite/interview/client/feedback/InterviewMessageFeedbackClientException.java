package com.daon.rewrite.interview.client.feedback;

/**
 * 외부 호출 실패와 JSON 변환·출력 내용 검증 실패를 구분해 Job 계층에 전달한다.
 * Worker가 reason을 실패 처리에 넘기면 트랜잭션 서비스가 최종 Job 오류 코드로 변환한다.
 */
public class InterviewMessageFeedbackClientException extends RuntimeException {

    private final Reason reason;

    private InterviewMessageFeedbackClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static InterviewMessageFeedbackClientException outputValidationFailed() {
        return new InterviewMessageFeedbackClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "면접 답변 피드백 결과 구조가 올바르지 않습니다.",
                null
        );
    }

    public static InterviewMessageFeedbackClientException outputValidationFailed(Throwable cause) {
        return new InterviewMessageFeedbackClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "면접 답변 피드백 결과를 변환할 수 없습니다.",
                cause
        );
    }

    public static InterviewMessageFeedbackClientException providerError(Throwable cause) {
        return new InterviewMessageFeedbackClientException(
                Reason.PROVIDER_ERROR,
                "면접 답변 피드백 provider 호출에 실패했습니다.",
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
