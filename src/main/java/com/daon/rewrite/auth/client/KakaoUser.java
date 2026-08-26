package com.daon.rewrite.auth.client;

public record KakaoUser(
        String providerUserId,
        String nickname,
        String profileImageUrl
) {
}
