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

/*
Springdoc의 OperationCustomizer는 Controller mapping 하나가 OpenAPI의 operation으로 변환될 때 호출되는 확작 지점
Controller -> Springdoc이 operation 객체 생성 -> OperationCustomizer 가 operation 객체를 변환
 */
public class RewriteApiOperationCustomizer implements OperationCustomizer {

    // 현재 OpenAPI문서에 등록된 ErrorResponse schema를 사용하라는 OpenAPI 문서 내부의 참조 경로
    private static final String ERROR_RESPONSE_SCHEMA = "#/components/schemas/ErrorResponse";

    /**
     *
     * @param operation     Springdoc이 Controller와 DTO를 분석해서 생성한 OpenAPI operation
     * @param handlerMethod operation을 만든 실제 Controller메서드(annotation정보를 조회할 수 있다.)
     * @return
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Controller 메서드에 선언된 @RewriteApi를 가져온다
        RewriteApi api = handlerMethod.getMethodAnnotation(RewriteApi.class);
        if (api == null) {
            return operation;
        }

        // 전체 오류 목록 생성
        List<ErrorCase> errorCases = errorCases(api);

        // Operation 문서 설정
        operation.setOperationId(api.id());
        operation.setSummary(api.id() + " · " + api.summary());
        operation.setTags(List.of(api.tag()));
        operation.setDescription(description(api, errorCases));
        // x-rewrite-* 구조화 데이터 추가 (OpenAPI 문서의 완성도를 높이기 위해 테스트에서 문서 누락을 자동으로 검사하게 만든다.)
        addExtensions(operation, api, errorCases);
        // 성공 HTTP 상태 보정
        configureSuccessResponse(operation, api);
        // 오류 response 와 example 추가
        addErrorResponses(operation, errorCases);

        return operation;
    }

    private List<ErrorCase> errorCases(RewriteApi api) {
        Map<String, ErrorCase> cases = new LinkedHashMap<>();
        // 일반 JSON 오류 반환
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

        // Redirect 오류 반환
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

        // authenticated = true인 API에 공통 401 자동 추가
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
        // csrfProtected = true인 API에 공통 403 자동 추가
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
        // 공통 500 자동 추가
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

    // Swagger UI에서 API를 펼쳤을 때 표시할 Markdown 설명을 생성
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

    // 설명용 오류 표
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

    // 표 깨짐 방지
    private String markdownCell(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>");
    }

    // API 정보를 OpenAPI vendor extension으로 추가 (기계가 읽는 구조화 정보)
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
