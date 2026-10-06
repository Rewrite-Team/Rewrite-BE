package com.daon.rewrite.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class CsrfTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final byte[] SIGNING_CONTEXT =
            "rewrite-csrf-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_TOKEN_LENGTH = 256;

    private final SecretKey secretKey;
    private final Clock clock;

    // 토큰 형식: 만료 시각(epoch seconds).난수.HMAC 서명
    public String issue() {
        String payload = Instant.now(clock).plus(TOKEN_TTL).getEpochSecond()
                + "." + SecureTokenSupport.randomToken();
        return payload + "." + encode(sign(payload));
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }

        try {
            long expiresAt = Long.parseLong(parts[0]);
            if (expiresAt <= Instant.now(clock).getEpochSecond()) {
                return false;
            }
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            // 서명 비교의 시간 차이를 줄여 timing attack 위험을 낮춘다.
            return MessageDigest.isEqual(expectedSignature, actualSignature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            // CSRF 전용 context로 다른 용도의 서명과 구분한다.
            mac.update(SIGNING_CONTEXT);
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CSRF token signing failed", e);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
