package com.daon.rewrite.auth.config;

import org.springframework.http.ResponseCookie;

public final class AuthCookieFactory {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final long ACCESS_TOKEN_MAX_AGE_SECONDS = 1800;
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 1209600;

    private AuthCookieFactory() {
    }

    public static ResponseCookie accessToken(String value) {
        return authCookie(ACCESS_TOKEN_COOKIE, value, "/", ACCESS_TOKEN_MAX_AGE_SECONDS);
    }

    public static ResponseCookie refreshToken(String value) {
        return authCookie(REFRESH_TOKEN_COOKIE, value, "/auth", REFRESH_TOKEN_MAX_AGE_SECONDS);
    }

    public static ResponseCookie clearAccessToken() {
        return authCookie(ACCESS_TOKEN_COOKIE, "", "/", 0);
    }

    public static ResponseCookie clearRefreshToken() {
        return authCookie(REFRESH_TOKEN_COOKIE, "", "/auth", 0);
    }

    public static ResponseCookie cookie(String name, String value, String path, long maxAge) {
        return authCookie(name, value, path, maxAge);
    }

    private static ResponseCookie authCookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
