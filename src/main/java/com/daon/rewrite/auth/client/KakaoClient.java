package com.daon.rewrite.auth.client;

/**
 * 카카오 인가 코드를 로그인에 필요한 외부 사용자 정보로 교환하는 경계다.
 * Rewrite 사용자 저장과 자체 인증 토큰 발급은 호출한 로그인 서비스가 담당한다.
 */
public interface KakaoClient {

    KakaoUser getUser(String authorizationCode);
}
