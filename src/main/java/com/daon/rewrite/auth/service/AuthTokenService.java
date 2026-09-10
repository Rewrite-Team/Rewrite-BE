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

@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class AuthTokenService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    private final JwtEncoder jwtEncoder;        // JWT의 header와 claims 를 실제 JWT 문자열로 만들고 서명하는 Spring Security 객체 (JWT header + JWT claims + 비밀키 -> 최종 JWT 문자열)
    private final AuthProperties properties;
    private final Clock clock;

    public String issueAccessToken(String userId) {
        Instant now = Instant.now(clock);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())                // 누가 발급했는가
                .audience(List.of(properties.audience()))   // 어떤 서비스인가
                .issuedAt(now)                              // 언제 발급했는가
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))      // 언제 만료되는가
                .subject(userId)                            // 누구의 토큰인가
                .id(SecureTokenSupport.randomToken())       // token 고유 ID는 무엇인가?
                .claim("purpose", "access")     // JWT 표준 claim이 아닌 프로젝트 자체 custom claim 추가. token 용도는 무엇인가
                .build();
        // AuthSecurityConfig 에서 SecretKey Bean이 JwtEncoder에 주입됨
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String issueRefreshToken() {
        return SecureTokenSupport.randomToken();
    }

    public String hashRefreshToken(String refreshToken) {
        return SecureTokenSupport.sha256(refreshToken);
    }
}
