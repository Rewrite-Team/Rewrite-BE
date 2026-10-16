package com.daon.rewrite.global.response;

import com.daon.rewrite.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void publicHttpErrorKeepsItsCodeMessageAndEmptyDetails(ErrorCode code) {
        ErrorResponse response = ErrorResponse.of(code);
        JsonNode error = objectMapper.valueToTree(response).path("error");

        assertThat(response.error().code()).isEqualTo(code);
        assertThat(error.path("code").asString()).isEqualTo(code.getCode());
        assertThat(error.path("message").asString()).isEqualTo(code.getMessage());
        assertThat(error.has("details")).isTrue();
        assertThat(error.path("details").isArray()).isTrue();
        assertThat(error.path("details").isEmpty()).isTrue();
    }

    @Test
    void validationErrorKeepsItsPublicDetailsAndSuppliedMessage() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "입력값을 확인해주세요.",
                List.of(new ErrorResponse.ErrorDetail("title", "제목을 입력해주세요.")));
        JsonNode error = objectMapper.valueToTree(response).path("error");

        assertThat(error.path("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.path("message").asString()).isEqualTo("입력값을 확인해주세요.");
        assertThat(error.path("details").get(0).path("field").asString()).isEqualTo("title");
        assertThat(error.path("details").get(0).path("reason").asString()).isEqualTo("제목을 입력해주세요.");
    }
}
