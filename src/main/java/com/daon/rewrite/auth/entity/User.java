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

    // 직접 생성자를 호출하지 않고 정적 팩토리 메서드를 사용
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
