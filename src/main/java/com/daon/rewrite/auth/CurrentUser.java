package com.daon.rewrite.auth;

public record CurrentUser(
        String id,
        String nickname,
        String profileImageUrl
) {
}
