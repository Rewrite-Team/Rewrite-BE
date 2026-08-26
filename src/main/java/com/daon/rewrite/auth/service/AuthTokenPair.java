package com.daon.rewrite.auth.service;

public record AuthTokenPair(
        String accessToken,
        String refreshToken
) {
}
