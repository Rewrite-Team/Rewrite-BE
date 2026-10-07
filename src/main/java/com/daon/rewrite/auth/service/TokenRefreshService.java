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

/**
 * refresh token을 한 번 사용해 새 access token과 refresh token으로 교체한다.
 * 기존 토큰의 폐기 상태와 새 토큰의 해시를 DB에 반영하고, 원문은 컨트롤러의 Cookie 발급용으로 반환한다.
 */
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    /**
     * 저장된 토큰을 잠금 조회하고 만료·폐기 여부를 확인한 뒤, 기존 토큰 폐기와 새 토큰 저장을 함께 커밋한다.
     * 같은 토큰의 동시 갱신은 행 잠금으로 직렬화하며, 먼저 성공한 요청 이후에는 기존 토큰을 다시 사용할 수 없다.
     * 누락·미등록·만료·폐기된 토큰은 모두 UNAUTHORIZED 예외로 처리하며, Cookie 만료는 컨트롤러가 담당한다.
     */
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
