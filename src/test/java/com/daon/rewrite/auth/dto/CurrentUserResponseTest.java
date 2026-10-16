package com.daon.rewrite.auth.dto;

import com.daon.rewrite.auth.AuthProvider;
import com.daon.rewrite.auth.CurrentUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserResponseTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void providerKeepsItsPublicString(AuthProvider provider) {
        CurrentUser user = new CurrentUser("user_test", "nickname", null, provider,
                Instant.parse("2026-01-01T00:00:00Z"));

        CurrentUserResponse response = CurrentUserResponse.from(user);
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(response.provider()).isEqualTo(provider);
        assertThat(json.path("provider").asString()).isEqualTo(provider.name());
        assertThat(json.has("profileImageUrl")).isTrue();
        assertThat(json.path("profileImageUrl").isNull()).isTrue();
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.parse("2026-01-01T09:00:00"));
    }
}
