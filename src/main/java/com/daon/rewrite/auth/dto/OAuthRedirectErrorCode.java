package com.daon.rewrite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 카카오 로그인 실패 redirect의 error query로 전달하는 공개 오류 코드. */
@Schema(enumAsRef = true)
public enum OAuthRedirectErrorCode {
    KAKAO_LOGIN_CANCELED,
    KAKAO_LOGIN_FAILED
}
