package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전달된 refresh token을 서버에서 폐기하는 로그아웃 처리를 담당한다.
 * Cookie 만료는 컨트롤러가 처리하며, 이미 발급된 access JWT를 서버에서 폐기하는 기능은 두지 않는다.
 */
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    /**
     * refresh token을 잠금 조회해 유효한 경우만 폐기하고, 누락·미등록·만료·이미 폐기된 토큰은 변경 없이 종료한다.
     * 갱신과 같은 행 잠금을 사용하지만 응답 도착 순서는 제어하지 않으므로, 클라이언트는 진행 중인 갱신을 마친 뒤 로그아웃한다.
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }

        Instant now = Instant.now(clock);
        refreshTokenRepository
                .findByTokenHashForUpdate(authTokenService.hashRefreshToken(refreshTokenValue))
                .filter(token -> token.isActive(now))
                .ifPresent(token -> token.revoke(now));
    }
}
