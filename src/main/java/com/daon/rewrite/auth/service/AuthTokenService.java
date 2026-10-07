package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Rewrite 인증에 사용하는 access JWT와 난수 refresh token을 생성한다.
 * refresh token의 DB 저장·폐기는 로그인·갱신·로그아웃 서비스가, Cookie 발급은 컨트롤러가 담당한다.
 */
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class AuthTokenService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * 사용자 ID를 subject로 담은 30분 수명의 access JWT를 발급한다.
     * issuer·audience·purpose는 인증 설정의 JWT 검증 조건과 맞춘다.
     */
    public String issueAccessToken(String userId) {
        Instant now = Instant.now(clock);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .subject(userId)
                .id(SecureTokenSupport.randomToken())
                .claim("purpose", "access")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String issueRefreshToken() {
        return SecureTokenSupport.randomToken();
    }

    public String hashRefreshToken(String refreshToken) {
        return SecureTokenSupport.sha256(refreshToken);
    }
}
