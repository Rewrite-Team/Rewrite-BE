package com.daon.rewrite.auth;

import java.time.Instant;

/** 인증 토큰이나 JPA 엔티티를 전달하지 않고 도메인 서비스에서 사용할 현재 사용자 정보. */
public record CurrentUser(
        String id,
        String nickname,
        String profileImageUrl,
        AuthProvider provider,
        Instant createdAt
) {
}
