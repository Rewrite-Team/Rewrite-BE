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
@Profile("!local & !test")
@RequiredArgsConstructor
public class CsrfTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final byte[] SIGNING_CONTEXT =
            "rewrite-csrf-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_TOKEN_LENGTH = 256;

    private final SecretKey secretKey;
    private final Clock clock;

    // 만료시각.난수.서명
    public String issue() {
        // getEpochSecond()= Java epoch인 1970-01-01T00:00:00Z부터의 초 개수를 반환. (만료시각 비교를 단순화 하기 위해 사용)
        String payload = Instant.now(clock).plus(TOKEN_TTL).getEpochSecond()
                + "." + SecureTokenSupport.randomToken();   // 암호학적으로 예측하기 어려운 안전한 난수 생성
        return payload + "." + encode(sign(payload));   // 서버 비밀키로 HMAC-SHA256 서명을 만들고 payload 뒤에 Base64 URL 형식의 서명을 붙인다
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }

        /*
        토큰은 정확히 다음과 같은 세 부분으로 구성되어 있어야 한다
        1787567400.random.signature
        parts[0] = 만료 시각
        parts[1] = 난수
        parts[2] = 서명
         */
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }

        try {
            // 만료시각 파싱
            // 첫 부분이 숫자가 아니면 NumberFormatException이 발생 (NumberFormatException은 IllegalArgumentException의 하위 타입)
            long expiresAt = Long.parseLong(parts[0]);
            // 현재시각이 만료 시각과 같거나 그 이후면 거부
            if (expiresAt <= Instant.now(clock).getEpochSecond()) {
                return false;
            }
            // 토큰에 들어있는 서명 문자열을 32바이트 값으로 복원
            // Base64 URL 형식이 잘못되었다면 IllegalArgumentException 발생
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            // 클라이언트가 보낸 만료시각과 난수를 이용하여 서버가 HMAC 서명을 다시 계산
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            /*
            actualSignature(클라이언트가 보낸 토큰 안의 서명) 과 expectedSignature(서버가 비밀키로 다시 계산한 서명) 이 동일한지 비교
            Arrays.equals(expectedSignature, actualSignature) 처럼 비교할 수도 있으나, 보안관련하여는 MessageDigest.isEqual()를 사용
            이유: 서명 비교 과정에서 발생할 수 있는 시간 차이를 줄이는 constant-time comparison 성격의 비교를 제공하기에
                 공격자가 비교 시간을 반복 측정하여 올바른 서명의 일부를 추측하는 timing attack 위험을 줄일 수 있다.
             */
            return MessageDigest.isEqual(expectedSignature, actualSignature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String payload) {
        try {
            // 일반 SHA-256은 누구나 동일한 해시를 만들 수 있는 반면 HMAC은 비밀키를 아는 서버만 올바른 인증 코드를 만들 수 있다.
            Mac mac = Mac.getInstance("HmacSHA256");
            // 서버 비밀키를 HMAC 계산기에 설정
            mac.init(secretKey);
            mac.update(SIGNING_CONTEXT);
            // 최종 HMAC-SHA256 결과를 생성
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CSRF token signing failed", e);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
