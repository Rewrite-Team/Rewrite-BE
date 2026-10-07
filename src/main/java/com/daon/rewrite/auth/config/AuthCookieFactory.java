package com.daon.rewrite.auth.config;

import org.springframework.http.ResponseCookie;

/**
 * 로그인·갱신·로그아웃 응답에서 사용할 Cookie의 보안 속성과 전송 경로를 통일한다.
 * 모든 Cookie는 HttpOnly·Secure이며, 인증 Cookie는 프론트엔드의 cross-site 요청을 위해 SameSite=None을 쓴다.
 */
public final class AuthCookieFactory {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final long ACCESS_TOKEN_MAX_AGE_SECONDS = 1800;
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 1209600;

    private AuthCookieFactory() {
    }

    /** 전체 API의 요청 인증에 쓰므로 Path=/, 수명은 access token과 같은 30분이다. */
    public static ResponseCookie accessToken(String value) {
        return cookie(ACCESS_TOKEN_COOKIE, value, "/", ACCESS_TOKEN_MAX_AGE_SECONDS, "None");
    }

    /** 갱신·로그아웃에서 읽는 refresh token은 /auth 하위에만 전송하며 14일 동안 유지한다. */
    public static ResponseCookie refreshToken(String value) {
        return cookie(REFRESH_TOKEN_COOKIE, value, "/auth", REFRESH_TOKEN_MAX_AGE_SECONDS, "None");
    }

    /** 발급 때와 같은 이름·Path에 Max-Age=0을 지정해 브라우저의 access Cookie를 만료시킨다. */
    public static ResponseCookie clearAccessToken() {
        return cookie(ACCESS_TOKEN_COOKIE, "", "/", 0, "None");
    }

    /** 발급 때와 같은 이름·Path에 Max-Age=0을 지정해 브라우저의 refresh Cookie를 만료시킨다. */
    public static ResponseCookie clearRefreshToken() {
        return cookie(REFRESH_TOKEN_COOKIE, "", "/auth", 0, "None");
    }

    /** 카카오 callback의 최상위 GET 이동에도 전송할 OAuth nonce Cookie에 SameSite=Lax를 적용한다. */
    public static ResponseCookie cookie(String name, String value, String path, long maxAge) {
        return cookie(name, value, path, maxAge, "Lax");
    }

    private static ResponseCookie cookie(
            String name,
            String value,
            String path,
            long maxAge,
            String sameSite
    ) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
