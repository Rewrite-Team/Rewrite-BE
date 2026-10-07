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

/**
 * 상태 변경 요청의 X-CSRF-Token 헤더에 사용할 서명 토큰을 발급·검증한다.
 * 서버에 토큰을 저장하지 않고 만료 시각과 HMAC 서명으로 검증하며, 사용자나 로그인 세션에 결합하지 않는다.
 */
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

    /**
     * 만료 시각(epoch seconds)·난수·Base64URL HMAC 서명을 점으로 연결한 토큰을 반환한다.
     * 서명은 만료 시각과 난수를 함께 보호하며, 발급한 토큰은 30분 동안 재사용할 수 있다.
     */
    public String issue() {
        String payload = Instant.now(clock).plus(TOKEN_TTL).getEpochSecond()
                + "." + SecureTokenSupport.randomToken();
        return payload + "." + encode(sign(payload));
    }

    /**
     * 전달된 토큰의 형식과 만료 시각을 확인한 뒤 같은 payload로 계산한 서명을 비교한다.
     * 입력 형식 오류·만료·서명 불일치는 false로 처리하고, 서명 계산 자체의 실패는 내부 오류로 전파한다.
     */
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
