package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.config.FrontendTarget;

public record KakaoLoginResult(
        KakaoLoginStatus status,
        AuthTokenPair tokens,
        FrontendTarget frontendTarget
) {

    public static KakaoLoginResult success(AuthTokenPair tokens, FrontendTarget frontendTarget) {
        return new KakaoLoginResult(KakaoLoginStatus.SUCCESS, tokens, frontendTarget);
    }

    public static KakaoLoginResult canceled(FrontendTarget frontendTarget) {
        return new KakaoLoginResult(KakaoLoginStatus.CANCELED, null, frontendTarget);
    }

    public static KakaoLoginResult failed(FrontendTarget frontendTarget) {
        return new KakaoLoginResult(KakaoLoginStatus.FAILED, null, frontendTarget);
    }
}
