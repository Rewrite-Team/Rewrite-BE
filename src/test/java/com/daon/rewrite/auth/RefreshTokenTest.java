package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.daon.rewrite.auth.entity.RefreshToken;
import com.daon.rewrite.auth.entity.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void tokenIsActiveOnlyBeforeExpirationAndRevocation() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        RefreshToken token = token(now.plusSeconds(60));

        assertThat(token.isActive(now)).isTrue();
        assertThat(token.isActive(now.plusSeconds(60))).isFalse();

        token.revoke(now.plusSeconds(30));

        assertThat(token.isActive(now.plusSeconds(30))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(now.plusSeconds(30));
    }

    private static RefreshToken token(Instant expiresAt) {
        Instant createdAt = expiresAt.minusSeconds(120);
        User user = User.create(
                "user_refresh_token",
                AuthProvider.KAKAO,
                "kakao-refresh-token",
                "토큰 사용자",
                null,
                createdAt
        );
        return RefreshToken.create("token-hash", user, createdAt, expiresAt);
    }
}
