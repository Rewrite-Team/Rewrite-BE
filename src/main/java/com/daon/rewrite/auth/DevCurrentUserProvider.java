package com.daon.rewrite.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** {@code auth-dev}에서 로그인과 사용자 DB 조회 없이 고정 사용자를 제공한다. */
@Component
@Profile("auth-dev")
public class DevCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser currentUser() {
        return new CurrentUser(
                "user_dev_001",
                "개발 사용자",
                "https://dev-profile.png",
                AuthProvider.KAKAO,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
