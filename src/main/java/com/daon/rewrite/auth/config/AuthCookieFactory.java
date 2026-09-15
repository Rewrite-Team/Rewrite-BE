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
        return cookie(ACCESS_TOKEN_COOKIE, value, "/", ACCESS_TOKEN_MAX_AGE_SECONDS, "None");
    }

    public static ResponseCookie refreshToken(String value) {
        return cookie(REFRESH_TOKEN_COOKIE, value, "/auth", REFRESH_TOKEN_MAX_AGE_SECONDS, "None");
    }

    public static ResponseCookie clearAccessToken() {
        return cookie(ACCESS_TOKEN_COOKIE, "", "/", 0, "None");
    }

    public static ResponseCookie clearRefreshToken() {
        return cookie(REFRESH_TOKEN_COOKIE, "", "/auth", 0, "None");
    }

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
