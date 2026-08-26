package com.daon.rewrite.auth.dto;

import com.daon.rewrite.auth.CurrentUser;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record CurrentUserResponse(
        String id,
        String nickname,
        String profileImageUrl,
        String provider,
        LocalDateTime createdAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static CurrentUserResponse from(CurrentUser user) {
        return new CurrentUserResponse(
                user.id(),
                user.nickname(),
                user.profileImageUrl(),
                user.provider().name(),
                LocalDateTime.ofInstant(user.createdAt(), SEOUL)
        );
    }
}
