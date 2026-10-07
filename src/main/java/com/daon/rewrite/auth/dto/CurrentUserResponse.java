package com.daon.rewrite.auth.dto;

import com.daon.rewrite.auth.CurrentUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Schema(requiredProperties = {"id", "nickname", "profileImageUrl", "provider", "createdAt"})
public record CurrentUserResponse(
        String id,
        String nickname,
        @Schema(nullable = true)
        String profileImageUrl,
        String provider,
        LocalDateTime createdAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 내부 Instant를 API의 Asia/Seoul 기준 offset 없는 날짜·시간으로 변환한다.
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
