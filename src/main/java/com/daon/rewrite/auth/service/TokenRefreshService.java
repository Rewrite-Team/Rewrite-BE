package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.entity.RefreshToken;
import com.daon.rewrite.auth.repository.RefreshTokenRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!local & !test")
@RequiredArgsConstructor
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    @Transactional
    public AuthTokenPair refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Instant now = Instant.now(clock);
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(authTokenService.hashRefreshToken(refreshTokenValue))
                .filter(token -> token.isActive(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        currentToken.revoke(now);

        String newRefreshToken = authTokenService.issueRefreshToken();

        refreshTokenRepository.save(RefreshToken.create(
                authTokenService.hashRefreshToken(newRefreshToken),
                currentToken.getUser(),
                now,
                now.plus(AuthTokenService.REFRESH_TOKEN_TTL)
        ));

        return new AuthTokenPair(
                authTokenService.issueAccessToken(currentToken.getUser().getId()),
                newRefreshToken
        );
    }
}
