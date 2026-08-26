package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.daon.rewrite.auth.service.CsrfTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

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
