package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.daon.rewrite.auth.service.CsrfTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * 같은 서명 키와 고정 Clock으로 발급·검증 시각을 분리해 실제 대기 없이 만료 경계를 검증한다.
 * 서명된 payload를 변조한 경우도 정상 토큰과 구분해 확인한다.
 */
class CsrfTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final SecretKeySpec SECRET_KEY = new SecretKeySpec(
            "01234567890123456789012345678901".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            "HmacSHA256"
    );

    @Test
    void tokenIsValidUntilItsThirtyMinuteExpiry() {
        CsrfTokenService issuer = serviceAt(NOW);
        String token = issuer.issue();

        assertThat(issuer.isValid(token)).isTrue();
        assertThat(serviceAt(NOW.plusSeconds(1799)).isValid(token)).isTrue();
        assertThat(serviceAt(NOW.plusSeconds(1800)).isValid(token)).isFalse();
    }

    @Test
    void rejectsMissingMalformedAndTamperedTokens() {
        CsrfTokenService service = serviceAt(NOW);
        String token = service.issue();
        // 서명은 그대로 두고 만료 시각의 첫 자리만 바꿔 payload 변조를 만든다.
        String tampered = (token.startsWith("1") ? "2" : "1") + token.substring(1);

        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("")).isFalse();
        assertThat(service.isValid("not-a-token")).isFalse();
        assertThat(service.isValid(tampered)).isFalse();
    }

    private static CsrfTokenService serviceAt(Instant instant) {
        return new CsrfTokenService(
                SECRET_KEY,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
