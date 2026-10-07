package com.daon.rewrite.global.openapi;

import com.daon.rewrite.global.exception.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Springdoc이 controller·DTO에서 만든 operation에 @RewriteApi의 사용 맥락과 오류 정책을 결합한다.
 * Swagger의 설명과 x-rewrite 메타데이터를 같은 선언에서 만들고, JSON 오류와 redirect 오류를 구분해 응답에 반영한다.
 * HTTP 응답 자체를 처리하는 코드가 아니라 생성 OpenAPI를 보완하는 확장 지점이다.
 */
public class RewriteApiOperationCustomizer implements OperationCustomizer {

    // OpenApiConfig가 등록하는 공통 오류 모델을 참조한다.
    private static final String ERROR_RESPONSE_SCHEMA = "#/components/schemas/ErrorResponse";

    /**
     * {@code @RewriteApi}가 없는 메서드는 Springdoc 결과를 유지하고, 있는 메서드만 Rewrite 문서 규칙을 적용한다.
     * 메타데이터를 합성한 뒤 성공 상태를 보정하고 API별·공통 오류 응답을 추가한다.
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        RewriteApi api = handlerMethod.getMethodAnnotation(RewriteApi.class);
        if (api == null) {
            return operation;
        }

        List<ErrorCase> errorCases = errorCases(api);

        operation.setOperationId(api.operationId());
        operation.setSummary(api.id() + " · " + api.summary());
        operation.setTags(List.of(api.tag()));
        operation.setDescription(description(api, errorCases));
        addExtensions(operation, api, errorCases);
        configureSuccessResponse(operation, api);
        addErrorResponses(operation, errorCases);

        return operation;
    }

    /**
     * API별 JSON·redirect 오류와 선택된 공통 401·403·500 오류를 코드 기준으로 합친다.
     * 명시한 오류 조건·처리 방법이 있으면 공통 정의가 덮어쓰지 않도록 우선한다.
     */
    private List<ErrorCase> errorCases(RewriteApi api) {
        Map<String, ErrorCase> cases = new LinkedHashMap<>();
        Arrays.stream(api.errors())
                .map(error -> new ErrorCase(
                        error.code().getStatus().value(),
                        error.code().getCode(),
                        error.code().getMessage(),
                        error.condition(),
                        error.action(),
                        error.detailField(),
                        error.detailReason(),
                        true
                ))
                .forEach(error -> cases.put(error.code(), error));

        Arrays.stream(api.redirectErrors())
                .map(error -> new ErrorCase(
                        error.status(),
                        error.code(),
                        "",
                        error.condition(),
                        error.action(),
                        "",
                        "",
                        false
                ))
                .forEach(error -> cases.put(error.code(), error));

        if (api.authenticated()) {
            cases.putIfAbsent(ErrorCode.UNAUTHORIZED.getCode(), new ErrorCase(
                    ErrorCode.UNAUTHORIZED.getStatus().value(),
                    ErrorCode.UNAUTHORIZED.getCode(),
                    ErrorCode.UNAUTHORIZED.getMessage(),
                    "access token이 없거나 만료됨",
                    "API-004로 토큰을 한 번 갱신하고 원 요청을 한 번 재시도한다. 실패하면 로그인 화면으로 이동한다.",
                    "",
                    "",
                    true
            ));
        }
        if (api.csrfProtected()) {
            cases.putIfAbsent(ErrorCode.CSRF_TOKEN_INVALID.getCode(), new ErrorCase(
                    ErrorCode.CSRF_TOKEN_INVALID.getStatus().value(),
                    ErrorCode.CSRF_TOKEN_INVALID.getCode(),
                    ErrorCode.CSRF_TOKEN_INVALID.getMessage(),
                    "CSRF 토큰 누락·만료·불일치",
                    "API-003으로 토큰을 재발급하고 원 요청을 한 번 재시도한다.",
                    "",
                    "",
                    true
            ));
        }
        if (api.includeInternalError()) {
            cases.putIfAbsent(ErrorCode.INTERNAL_ERROR.getCode(), new ErrorCase(
                    ErrorCode.INTERNAL_ERROR.getStatus().value(),
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    ErrorCode.INTERNAL_ERROR.getMessage(),
                    "예상하지 못한 서버 오류",
                    "공통 일시 오류를 표시하며 상태 변경 요청은 자동 재전송하지 않는다.",
                    "",
                    "",
                    true
            ));
        }

        return List.copyOf(cases.values());
    }

    // Swagger에서 API를 펼쳤을 때 사용 맥락과 오류 대응을 함께 읽을 수 있는 Markdown 설명을 만든다.
    private String description(RewriteApi api, List<ErrorCase> errorCases) {
        return """
                > 인증: %s · CSRF: %s

                ### 사용 목적
                %s

                ### 사용 화면
                %s

                ### 호출 시점
                %s

                ### 주요 동작
                %s

                ### 성공 후 처리
                %s

                ### 오류
                %s
                """.formatted(
                api.authenticated() ? "필요" : "불필요",
                api.csrfProtected() ? "필요" : "불필요",
                api.purpose(),
                String.join(", ", api.screens()),
                api.trigger(),
                api.behavior(),
                api.success(),
                errorTable(errorCases)
        );
    }

    // API 설명의 오류 표는 HTTP 상태 순으로 표시한다.
    private String errorTable(List<ErrorCase> errorCases) {
        StringBuilder table = new StringBuilder("| HTTP | 오류 코드 | 발생 조건 | 처리 |\n")
                .append("|---:|---|---|---|\n");

        errorCases.stream()
                .sorted((left, right) -> Integer.compare(
                        left.status(),
                        right.status()
                ))
                .forEach(error -> table
                        .append("| ").append(error.status())
                        .append(" | `").append(error.code()).append("`")
                        .append(" | ").append(markdownCell(error.condition()))
                        .append(" | ").append(markdownCell(error.action()))
                        .append(" |\n"));

        return table.toString();
    }

    // 조건·처리 문장의 구분자나 개행이 Markdown 표 구조를 바꾸지 않게 한다.
    private String markdownCell(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>");
    }

    // 화면용 Markdown과 같은 정보를 도구·테스트가 읽을 수 있는 구조화된 x-rewrite 확장으로도 제공한다.
    private void addExtensions(Operation operation, RewriteApi api, List<ErrorCase> errorCases) {
        operation.addExtension("x-rewrite-api-id", api.id());
        operation.addExtension("x-rewrite-purpose", api.purpose());
        operation.addExtension("x-rewrite-screens", List.of(api.screens()));
        operation.addExtension("x-rewrite-trigger", api.trigger());
        operation.addExtension("x-rewrite-behavior", api.behavior());
        operation.addExtension("x-rewrite-success", api.success());
        operation.addExtension("x-rewrite-success-status", api.successStatus());
        operation.addExtension("x-rewrite-authenticated", api.authenticated());
        operation.addExtension("x-rewrite-csrf-protected", api.csrfProtected());
        operation.addExtension("x-rewrite-errors", errorCases.stream()
                .map(this::errorExtension)
                .toList());
    }

    private Map<String, Object> errorExtension(ErrorCase error) {
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("status", error.status());
        extension.put("code", error.code());
        extension.put("condition", error.condition());
        extension.put("action", error.action());
        return extension;
    }

    /**
     * 200 외 성공 상태가 선언된 API는 추론된 200 응답을 선언 상태로 옮긴다.
     * 해당 상태의 명시적 응답이 있으면 그 응답의 헤더·스키마를 유지하고 성공 설명을 설정한다.
     */
    private void configureSuccessResponse(Operation operation, RewriteApi api) {
        if (api.successStatus() == 200) {
            return;
        }

        ApiResponse inferred = operation.getResponses().remove("200");
        ApiResponse success = operation.getResponses().get(Integer.toString(api.successStatus()));
        if (success == null) {
            success = inferred == null ? new ApiResponse() : inferred;
        }
        success.setDescription(api.success());
        operation.getResponses().addApiResponse(Integer.toString(api.successStatus()), success);
    }

    // 같은 HTTP 상태의 여러 오류를 하나의 응답으로 모으고 오류 코드별 예시는 그 안에서 구분한다.
    private void addErrorResponses(Operation operation, List<ErrorCase> errorCases) {
        Map<Integer, List<ErrorCase>> casesByStatus = new TreeMap<>();
        errorCases.forEach(error -> casesByStatus
                .computeIfAbsent(error.status(), ignored -> new ArrayList<>())
                .add(error));

        casesByStatus.forEach((status, cases) -> operation.getResponses().addApiResponse(
                Integer.toString(status),
                errorResponse(operation.getResponses().get(Integer.toString(status)), cases)
        ));
    }

    /**
     * 기존 응답의 헤더와 설명을 유지하면서 같은 상태의 오류 설명을 합친다.
     * JSON 오류에만 ErrorResponse 참조와 코드별 named example을 붙이고, redirect 오류는 설명에 추가한다.
     */
    private ApiResponse errorResponse(ApiResponse existing, List<ErrorCase> errorCases) {
        ApiResponse response = existing == null ? new ApiResponse() : existing;
        String errorDescription = errorResponseDescription(errorCases);
        if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(errorDescription);
        } else {
            response.setDescription(response.getDescription() + "\n\n" + errorDescription);
        }

        List<ErrorCase> jsonErrors = errorCases.stream().filter(ErrorCase::json).toList();
        if (!jsonErrors.isEmpty()) {
            MediaType mediaType = new MediaType()
                    .schema(new Schema<>().$ref(ERROR_RESPONSE_SCHEMA));
            jsonErrors.forEach(error -> mediaType.addExamples(error.code(), errorExample(error)));
            response.setContent(new Content().addMediaType("application/json", mediaType));
        }
        return response;
    }

    private String errorResponseDescription(List<ErrorCase> errorCases) {
        StringBuilder description = new StringBuilder("| 오류 코드 | 발생 조건 | 처리 |\n")
                .append("|---|---|---|\n");
        errorCases.forEach(error -> description
                .append("| `").append(error.code()).append("`")
                .append(" | ").append(markdownCell(error.condition()))
                .append(" | ").append(markdownCell(error.action()))
                .append(" |\n"));
        return description.toString();
    }

    private Example errorExample(ErrorCase error) {
        List<Map<String, String>> details = validationDetails(error);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.code());
        body.put("message", error.message());
        body.put("details", details);

        return new Example()
                .summary(error.condition())
                .description(error.action())
                .value(Map.of("error", body));
    }

    /**
     * VALIDATION_ERROR에만 선언된 대표 field·reason 쌍을 details 예시로 사용한다.
     * 둘 중 하나만 지정한 문서는 생성 시 거부하고, 둘 다 없으면 빈 details로 표현한다.
     */
    private List<Map<String, String>> validationDetails(ErrorCase error) {
        if (!ErrorCode.VALIDATION_ERROR.getCode().equals(error.code())) {
            return List.of();
        }

        boolean hasField = !error.detailField().isBlank();
        boolean hasReason = !error.detailReason().isBlank();
        if (hasField != hasReason) {
            throw new IllegalStateException("VALIDATION_ERROR example은 detailField와 detailReason을 함께 설정해야 합니다.");
        }
        if (!hasField) {
            return List.of();
        }
        return List.of(Map.of("field", error.detailField(), "reason", error.detailReason()));
    }

    private record ErrorCase(
            int status,
            String code,
            String message,
            String condition,
            String action,
            String detailField,
            String detailReason,
            boolean json
    ) {
    }
}
