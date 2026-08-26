package com.daon.rewrite.auth;

import java.time.Instant;

public record CurrentUser(
        String id,
        String nickname,
        String profileImageUrl,
        AuthProvider provider,
        Instant createdAt
) {
}
