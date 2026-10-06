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

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

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
                .claim("purpose", "access") // 인증에는 access 용도의 JWT만 허용한다.
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
