package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openApiDocumentContainsApplicationInfoAndControllerPath() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.info.title").value("Rewrite API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/cover-letters']").exists());
    }

    @Test
    void representativeApisContainRewriteDocumentationMetadata() throws Exception {
        JsonNode document = openApiDocument();

        JsonNode api009 = operation(document, "/cover-letters/{coverLetterId}/basic-info", "put");
        assertThat(api009.path("operationId").asText()).isEqualTo("API-009");
        assertThat(api009.path("summary").asText()).isEqualTo("API-009 · 기본 정보 저장");
        assertThat(api009.path("tags").get(0).asText()).isEqualTo("자기소개서");
        assertThat(api009.path("x-rewrite-api-id").asText()).isEqualTo("API-009");
        assertThat(api009.path("x-rewrite-screens").get(0).asText())
                .isEqualTo("자기소개서 등록");
        assertThat(api009.path("x-rewrite-authenticated").asBoolean()).isTrue();
        assertThat(api009.path("x-rewrite-csrf-protected").asBoolean()).isTrue();
        assertThat(api009.path("description").asText())
                .contains("### 사용 목적", "### 사용 화면", "### 호출 시점", "### 주요 동작", "### 성공 후 처리", "### 오류");

        JsonNode api014 = operation(document, "/cover-letters/{coverLetterId}/submit", "post");
        assertThat(api014.path("operationId").asText()).isEqualTo("API-014");
        assertThat(api014.path("x-rewrite-screens").valueStream().map(JsonNode::asText).toList())
                .containsExactly("자기소개서 등록", "첨삭 진행");
        assertThat(api014.path("description").asText())
                .contains("최초 첨삭 실패 화면에서 수동 재시도", "REVIEWED이면 jobId=null", "API-012");
        assertThat(api014.path("responses").path("409").path("content")
                .path("application/json").path("examples").propertyStream()
                .map(entry -> entry.getKey())
                .toList())
                .containsExactlyInAnyOrder("CONFLICT", "LLM_JOB_ALREADY_RUNNING");

        JsonNode api016 = operation(document, "/llm-jobs/{jobId}/stream", "get");
        assertThat(api016.path("operationId").asText()).isEqualTo("API-016");
        assertThat(api016.path("x-rewrite-screens").valueStream().map(JsonNode::asText).toList())
                .containsExactly("첨삭 진행", "키워드 분석", "AI 면접");
        assertThat(api016.path("x-rewrite-csrf-protected").asBoolean()).isFalse();
        assertThat(api016.path("description").asText())
                .contains(
                        "job.state.status=FAILED",
                        "HTTP ErrorResponse가 아니다",
                        "최종 job.state와 도메인별 후속 이벤트 전송을 마친 뒤 연결을 종료"
                );
        assertThat(api016.path("responses").has("403")).isFalse();
        assertThat(api016.path("responses").path("200").path("content")
                .path("text/event-stream").path("schema").path("example").asText())
                .contains("event: job.state", "event: review.questions");

        JsonNode api030 = operation(document, "/cover-letters/stream", "get");
        assertThat(api030.path("responses").path("200").path("content")
                .path("text/event-stream").path("schema").path("example").asText())
                .contains("cover-letter.review-status.snapshot", "cover-letter.review-status.changed");
    }

    @Test
    void documentedErrorsUseSharedSchemaAndNamedExamples() throws Exception {
        JsonNode document = openApiDocument();
        assertThat(document.path("components").path("schemas").has("ErrorResponse")).isTrue();
        assertThat(document.path("components").path("schemas").has("ErrorBody")).isTrue();
        assertThat(document.path("components").path("schemas").has("ErrorDetail")).isTrue();
        assertThat(document.path("components").path("schemas").path("ErrorResponse")
                .path("properties").path("error").path("$ref").asText())
                .isEqualTo("#/components/schemas/ErrorBody");

        JsonNode api009 = operation(document, "/cover-letters/{coverLetterId}/basic-info", "put");
        for (String status : List.of("400", "401", "403", "404", "409", "500")) {
            JsonNode response = api009.path("responses").path(status);
            assertThat(response.isMissingNode()).isFalse();
            assertThat(response.path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }

        JsonNode validationExample = api009.path("responses").path("400").path("content")
                .path("application/json").path("examples").path("VALIDATION_ERROR").path("value");
        assertThat(validationExample.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(validationExample.path("error").path("details").get(0).path("field").asText())
                .isEqualTo("jobPostingUrl");
        assertThat(validationExample.path("error").path("details").get(0).path("reason").asText())
                .isEqualTo("공고 링크 형식이 올바르지 않습니다.");

        JsonNode api014ValidationExample = operation(document, "/cover-letters/{coverLetterId}/submit", "post")
                .path("responses").path("400").path("content")
                .path("application/json").path("examples").path("VALIDATION_ERROR").path("value");
        assertThat(api014ValidationExample.path("error").path("details").get(0).path("field").asText())
                .isEqualTo("preferences");
        assertThat(api014ValidationExample.path("error").path("details").get(0).path("reason").asText())
                .isEqualTo("채용 우대사항을 입력해야 합니다.");

        JsonNode api016 = operation(document, "/llm-jobs/{jobId}/stream", "get");
        Set<String> httpErrorCodes = new HashSet<>();
        api016.path("responses").propertyStream().forEach(response -> response.getValue()
                .path("content").path("application/json").path("examples")
                .propertyStream().forEach(example -> httpErrorCodes.add(example.getKey())));
        assertThat(httpErrorCodes)
                .contains("UNAUTHORIZED", "NOT_FOUND", "INTERNAL_ERROR")
                .doesNotContain("LLM_PROVIDER_ERROR", "LLM_CONTEXT_LENGTH_EXCEEDED", "LLM_CONTENT_FILTERED");
    }

    @Test
    void rewriteApiOperationIdsAreUniqueAndFollowApiIdFormat() throws Exception {
        JsonNode paths = openApiDocument().path("paths");
        List<String> operationIds = new ArrayList<>();

        Iterator<JsonNode> pathItems = paths.elements();
        while (pathItems.hasNext()) {
            pathItems.next().elements().forEachRemaining(operation -> {
                if (operation.has("x-rewrite-api-id")) {
                    operationIds.add(operation.path("operationId").asText());
                }
            });
        }

        assertThat(operationIds).hasSize(24);
        assertThat(new HashSet<>(operationIds)).hasSameSizeAs(operationIds);
        assertThat(operationIds).allMatch(operationId -> operationId.matches("API-\\d{3}"));
        assertThat(operationIds).doesNotContain("API-028");
    }

    @Test
    void everyGeneratedRewriteOperationUsesTheStandardSectionsAndCanonicalScreens() throws Exception {
        JsonNode paths = openApiDocument().path("paths");
        Set<String> canonicalScreens = Set.of(
                "공통", "로그인", "자기소개서 목록", "자기소개서 등록",
                "첨삭 진행", "첨삭 결과", "키워드 분석", "AI 면접"
        );

        paths.elements().forEachRemaining(pathItem -> pathItem.elements().forEachRemaining(operation -> {
            if (!operation.has("x-rewrite-api-id")) {
                return;
            }
            assertThat(operation.path("description").asText()).contains(
                    "### 사용 목적", "### 사용 화면", "### 호출 시점",
                    "### 주요 동작", "### 성공 후 처리", "### 오류"
            );
            assertThat(operation.path("x-rewrite-screens").valueStream().map(JsonNode::asText).toList())
                    .allMatch(canonicalScreens::contains);
            assertThat(operation.path("x-rewrite-errors").isArray()).isTrue();
            assertThat(operation.path("x-rewrite-errors").isEmpty()).isFalse();
        }));
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    private JsonNode openApiDocument() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode operation(JsonNode document, String path, String method) {
        return document.path("paths").path(path).path(method);
    }
}
