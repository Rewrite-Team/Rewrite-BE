package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!local & !test")
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final Clock clock;

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
