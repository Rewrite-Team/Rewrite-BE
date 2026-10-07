package com.daon.rewrite.auth.client;

/**
 * 카카오 통신 오류와 로그인에 필요한 응답 필드 누락을 외부 연동 실패로 전달한다.
 * 로그인 서비스는 이 예외를 callback의 일반 로그인 실패 결과로 변환한다.
 */
public class KakaoClientException extends RuntimeException {

    public KakaoClientException(String message) {
        super(message);
    }

    public KakaoClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
