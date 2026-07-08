package com.daon.rewrite.keywordanalysis.client;

public class KeywordAnalysisClientException extends RuntimeException {

    private final Reason reason;

    private KeywordAnalysisClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static KeywordAnalysisClientException outputValidationFailed() {
        return new KeywordAnalysisClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "키워드 분석 결과 구조가 올바르지 않습니다.",
                null
        );
    }

    public static KeywordAnalysisClientException outputValidationFailed(Throwable cause) {
        return new KeywordAnalysisClientException(
                Reason.OUTPUT_VALIDATION_FAILED,
                "키워드 분석 결과를 변환할 수 없습니다.",
                cause
        );
    }

    public static KeywordAnalysisClientException providerError(Throwable cause) {
        return new KeywordAnalysisClientException(
                Reason.PROVIDER_ERROR,
                "키워드 분석 provider 호출에 실패했습니다.",
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
