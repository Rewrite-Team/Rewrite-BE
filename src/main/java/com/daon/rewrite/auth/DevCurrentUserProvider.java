package com.daon.rewrite.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile({"local", "test"})  // Spring profile 이 local 또는 test 일 때만 Spring Bean 으로 등록
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
