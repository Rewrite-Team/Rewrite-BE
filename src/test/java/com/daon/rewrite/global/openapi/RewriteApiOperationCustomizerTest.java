package com.daon.rewrite.global.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.daon.rewrite.auth.controller.KakaoAuthController;
import com.daon.rewrite.auth.controller.UserController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.method.HandlerMethod;

/** redirect 코드의 재사용 schema 참조와 기존 HTTP 응답 메타데이터 보존을 검증한다. */
class RewriteApiOperationCustomizerTest {

    private final RewriteApiOperationCustomizer customizer = new RewriteApiOperationCustomizer();

    @ParameterizedTest
    @ValueSource(strings = {"authorize", "callback"})
    void redirectOperationsReferenceCodeEnumAndKeepHeadersWithoutJsonBody(String method) throws Exception {
        Header location = new Header().schema(new StringSchema().format("uri"));
        Header cookie = new Header().schema(new StringSchema());
        ApiResponse redirect = new ApiResponse()
                .description("기존 redirect 설명")
                .addHeaderObject("Location", location)
                .addHeaderObject("Set-Cookie", cookie);
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("302", redirect));
        Class<?>[] parameters = method.equals("authorize")
                ? new Class<?>[]{String.class}
                : new Class<?>[]{String.class, String.class, String.class, String.class};

        customizer.customize(operation, new HandlerMethod(mock(KakaoAuthController.class), method, parameters));

        assertThat(operation.getExtensions()).containsEntry(
                "x-rewrite-redirect-error-code-schema",
                Map.of("$ref", "#/components/schemas/OAuthRedirectErrorCode")
        );
        List<?> errors = (List<?>) operation.getExtensions().get("x-rewrite-errors");
        List<String> codes = errors.stream().map(error -> (String) ((Map<?, ?>) error).get("code")).toList();
        if (method.equals("authorize")) {
            assertThat(codes).containsExactly("KAKAO_LOGIN_FAILED");
        } else {
            assertThat(codes).containsExactly("KAKAO_LOGIN_CANCELED", "KAKAO_LOGIN_FAILED");
        }
        assertThat(operation.getResponses()).containsOnlyKeys("302");
        assertThat(operation.getResponses().get("302")).isSameAs(redirect);
        assertThat(redirect.getHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "Location", location,
                "Set-Cookie", cookie
        ));
        assertThat(redirect.getDescription()).contains("KAKAO_LOGIN_FAILED");
        assertThat(redirect.getContent()).isNull();
    }

    @Test
    void jsonErrorOperationsDoNotExposeRedirectCodeSchema() throws Exception {
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));

        customizer.customize(operation, new HandlerMethod(mock(UserController.class), "me"));

        assertThat(operation.getExtensions()).doesNotContainKey("x-rewrite-redirect-error-code-schema");
        assertThat(operation.getResponses().get("401").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ErrorResponse");
    }
}
