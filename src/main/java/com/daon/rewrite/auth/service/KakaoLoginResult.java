package com.daon.rewrite.auth.service;

public record KakaoLoginResult(
        KakaoLoginStatus status,
        AuthTokenPair tokens
) {

    public static KakaoLoginResult success(AuthTokenPair tokens) {
        return new KakaoLoginResult(KakaoLoginStatus.SUCCESS, tokens);
    }

    public static KakaoLoginResult canceled() {
        return new KakaoLoginResult(KakaoLoginStatus.CANCELED, null);
    }

    public static KakaoLoginResult failed() {
        return new KakaoLoginResult(KakaoLoginStatus.FAILED, null);
    }
}
