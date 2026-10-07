package com.daon.rewrite.auth.entity;

import com.daon.rewrite.auth.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OAuth 계정을 Rewrite 내부 사용자 ID에 연결하는 계정이다.
 * provider와 providerUserId 조합으로 같은 외부 계정을 식별하고, 내부 ID는 인증 주체와 리소스 소유권 기준으로 사용한다.
 * 재로그인 시에는 계정 식별자를 유지하면서 카카오의 최신 닉네임·프로필 이미지로 갱신한다.
 */
@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_user",
                columnNames = {"provider", "provider_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private String providerUserId;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(
            String id,
            AuthProvider provider,
            String providerUserId,
            String nickname,
            String profileImageUrl,
            Instant now
    ) {
        this.id = id;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User create(
            String id,
            AuthProvider provider,
            String providerUserId,
            String nickname,
            String profileImageUrl,
            Instant now
    ) {
        return new User(id, provider, providerUserId, nickname, profileImageUrl, now);
    }

    public void updateProfile(String nickname, String profileImageUrl, Instant now) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = now;
    }
}
