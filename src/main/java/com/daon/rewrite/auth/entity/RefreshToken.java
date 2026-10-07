package com.daon.rewrite.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Rewrite 인증 갱신에 사용하는 토큰의 서버 측 기록이다.
 * 원문은 Cookie로 전달하고 DB에는 SHA-256 해시와 발급·만료·폐기 시각을 저장한다.
 * 발급 서비스는 14일 만료를 지정하며, 갱신 시 기존 기록을 폐기하고 새 토큰 기록을 만든다.
 * 로그아웃도 기존 기록을 폐기하므로 만료 전이라도 revokedAt이 있으면 재사용할 수 없다.
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user")
    )
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    private RefreshToken(String tokenHash, User user, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(String tokenHash, User user, Instant createdAt, Instant expiresAt) {
        return new RefreshToken(tokenHash, user, createdAt, expiresAt);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
