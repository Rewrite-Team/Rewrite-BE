package com.daon.rewrite.global.config;

import com.daon.rewrite.auth.dto.OAuthRedirectErrorCode;
import com.daon.rewrite.coverletter.dto.CoverLetterReviewStatusEventResponse;
import com.daon.rewrite.global.sse.SseEventName;
import com.daon.rewrite.llmjob.dto.InterviewFeedbackDeltaResponse;
import com.daon.rewrite.llmjob.dto.LlmJobStateEventResponse;
import com.daon.rewrite.llmjob.dto.ReviewQuestionsEventResponse;
import com.daon.rewrite.global.openapi.RewriteApiOperationCustomizer;
import com.daon.rewrite.global.response.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * controller·DTO에서 생성한 OpenAPI에 Rewrite 사용 맥락과 공통 스키마·CSRF 요청 표시를 결합한다.
 * 메서드별 설명·오류는 RewriteApiOperationCustomizer가, 문서 전체의 모델과 security 표시는 아래 커스터마이저가 보완한다.
 * 생성 문서는 API 사용 안내이며 실제 요청의 인증·CSRF 검증은 Security 설정이 담당한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String CSRF_SECURITY_SCHEME = "csrfToken";

    /**
     * API 문서의 기본 정보와 Swagger의 X-CSRF-Token 입력 수단을 정의한다.
     * 헤더 입력이 필요한 operation의 문서 표시는 csrfSecurityRequirementCustomizer에서 연결한다.
     */
    @Bean
    OpenAPI rewriteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rewrite API")
                        .description("API 번호, 사용 화면, 호출 흐름과 오류 조건을 확인하는 Rewrite 백엔드 핵심 API 문서")
                        .version("v1"))
                .schemaRequirement(CSRF_SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-CSRF-Token")
                        .description("GET /auth/csrf-token 응답의 csrfToken 값"));
    }

    /** Springdoc이 각 controller 메서드의 operation을 만든 뒤 @RewriteApi 정보를 합성할 확장 지점을 등록한다. */
    @Bean
    OperationCustomizer rewriteApiOperationCustomizer() {
        return new RewriteApiOperationCustomizer();
    }

    /** HTTP 본문에서 직접 참조되지 않는 오류·redirect·SSE 모델도 OpenAPI 3.1 components에 등록한다. */
    @Bean
    @Order(10)
    OpenApiCustomizer publicResponseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            Map<SseEventName, Class<?>> events = new LinkedHashMap<>();
            events.put(SseEventName.JOB_STATE, LlmJobStateEventResponse.class);
            events.put(SseEventName.REVIEW_QUESTIONS, ReviewQuestionsEventResponse.class);
            events.put(SseEventName.INTERVIEW_FEEDBACK_DELTA, InterviewFeedbackDeltaResponse.class);
            events.put(SseEventName.COVER_LETTER_REVIEW_STATUS_SNAPSHOT, CoverLetterReviewStatusEventResponse.Snapshot.class);
            events.put(SseEventName.COVER_LETTER_REVIEW_STATUS_CHANGED, CoverLetterReviewStatusEventResponse.Item.class);

            ModelConverters converters = ModelConverters.getInstance(true);
            for (Class<?> model : List.of(ErrorResponse.class, OAuthRedirectErrorCode.class, SseEventName.class)) {
                converters.readAll(model).forEach(openApi.getComponents()::addSchemas);
            }
            Map<SseEventName, Map<String, String>> payloads = new LinkedHashMap<>();
            events.forEach((event, model) -> {
                var resolved = converters.resolveAsResolvedSchema(new AnnotatedType(model).resolveAsRef(true));
                resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
                payloads.put(event, Map.of("$ref", resolved.schema.get$ref()));
            });
            openApi.getComponents().addSchemas("SseHeartbeatEvent", new Schema<>()
                    .types(Set.of("object"))
                    .additionalProperties(false));
            payloads.put(SseEventName.HEARTBEAT, Map.of("$ref", "#/components/schemas/SseHeartbeatEvent"));
            documentSseEvents(openApi, "/llm-jobs/{jobId}/stream", payloads,
                    SseEventName.JOB_STATE, SseEventName.REVIEW_QUESTIONS,
                    SseEventName.INTERVIEW_FEEDBACK_DELTA, SseEventName.HEARTBEAT);
            documentSseEvents(openApi, "/cover-letters/stream", payloads,
                    SseEventName.COVER_LETTER_REVIEW_STATUS_SNAPSHOT,
                    SseEventName.COVER_LETTER_REVIEW_STATUS_CHANGED, SseEventName.HEARTBEAT);
        };
    }

    private void documentSseEvents(OpenAPI openApi, String path,
            Map<SseEventName, Map<String, String>> payloads, SseEventName... events) {
        var operation = openApi.getPaths().get(path).getGet();
        Map<String, Map<String, String>> schemas = new LinkedHashMap<>();
        for (SseEventName event : events) {
            schemas.put(event.value(), payloads.get(event));
        }
        operation.addExtension("x-rewrite-sse-events", schemas);
        operation.addExtension("x-rewrite-sse-event-name-schema",
                Map.of("$ref", "#/components/schemas/SseEventName"));
    }

    /**
     * null을 허용하는 객체 참조를 oneOf의 객체 참조와 null 타입으로 표현한다.
     * 값이 null일 수 있다는 의미를 보완하며, DTO에서 지정한 필드 존재 여부(required)는 변경하지 않는다.
     */
    @Bean
    @Order(20)
    OpenApiCustomizer nullableReferenceSchemaCustomizer() {
        return openApi -> {
            for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
                if (schema.getProperties() == null) {
                    continue;
                }
                for (Schema<?> property : schema.getProperties().values()) {
                    if (property.get$ref() == null || property.getTypes() == null
                            || !property.getTypes().contains("null")) {
                        continue;
                    }
                    String reference = property.get$ref();
                    property.set$ref(null);
                    property.setTypes(null);
                    property.setOneOf(List.of(
                            new Schema<>().$ref(reference),
                            new Schema<>().types(Set.of("null"))
                    ));
                }
            }
        };
    }

    /**
     * 생성 문서의 POST·PUT·PATCH·DELETE에 Swagger의 CSRF 헤더 입력을 연결한다.
     * {@code @RewriteApi}의 csrfProtected 여부와 별개로 HTTP 메서드를 기준으로 문서의 security 항목을 추가한다.
     */
    @Bean
    OpenApiCustomizer csrfSecurityRequirementCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem -> {
            if (pathItem.getPost() != null) {
                pathItem.getPost().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getPut() != null) {
                pathItem.getPut().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getPatch() != null) {
                pathItem.getPatch().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getDelete() != null) {
                pathItem.getDelete().addSecurityItem(csrfSecurityRequirement());
            }
        });
    }

    private SecurityRequirement csrfSecurityRequirement() {
        return new SecurityRequirement().addList(CSRF_SECURITY_SCHEME);
    }
}
