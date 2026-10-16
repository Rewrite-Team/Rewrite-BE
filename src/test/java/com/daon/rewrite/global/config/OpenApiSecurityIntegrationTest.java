package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * prod의 auth-real 보안 필터와 인증 controller를 활성화하되 DB는 인메모리 H2로 대체한다.
 * 공개 문서 접근·대표 업무 API의 인증 필요 여부와 전체 API·OAuth redirect·응답 schema 계약을 검증한다.
 * 카카오 로그인 endpoint는 생성 문서에서 확인하며 이 테스트에서 외부 카카오 호출은 수행하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rewrite-prod-profile-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.openai.api-key=test-key",
        "rewrite.auth.jwt-secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "rewrite.auth.frontend-origin=https://rewrite.example.com",
        "rewrite.auth.frontend-success-url=https://rewrite.example.com/writing",
        "rewrite.auth.frontend-login-url=https://rewrite.example.com/login",
        "rewrite.auth.local-frontend-origin=http://localhost:3000",
        "rewrite.auth.local-frontend-success-url=http://localhost:3000/writing",
        "rewrite.auth.local-frontend-login-url=http://localhost:3000/login",
        "rewrite.auth.kakao.client-id=test-client",
        "rewrite.auth.kakao.client-secret=test-secret",
        "rewrite.auth.kakao.redirect-uri=https://api.rewrite.example.com/auth/kakao/callback"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class OpenApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openApiEndpointsArePublicInProductionProfileRegardlessOfAccessCookie() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html"}) {
            mockMvc.perform(get(path)
                            .cookie(new Cookie("access_token", "invalid-token")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void publicOpenApiDocumentIncludesAuthenticationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Rewrite API"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize']").exists())
                .andExpect(jsonPath("$.paths['/auth/kakao/callback']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.type")
                        .value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.in")
                        .value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.name")
                        .value("X-CSRF-Token"))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.security[0].csrfToken")
                        .isArray())
                .andExpect(jsonPath("$.paths['/auth/csrf-token'].get.security")
                        .doesNotExist());
    }

    @Test
    void productionProfileDocumentsAllActiveApisAndOAuthRedirects() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..operationId", hasSize(29)))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.operationId")
                        .value("startKakaoLogin"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.parameters[0].name")
                        .value("target"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.parameters[0].schema.default")
                        .value("production"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.parameters[0].schema.enum",
                        containsInAnyOrder("local", "production")))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['302'].description")
                        .value(org.hamcrest.Matchers.containsString("KAKAO_LOGIN_FAILED")))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['500']")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['302'].headers.Location")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/kakao/callback'].get.responses['302'].description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("KAKAO_LOGIN_CANCELED"),
                                org.hamcrest.Matchers.containsString("KAKAO_LOGIN_FAILED")
                        )))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/SuccessResponse"))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['401'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['200'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/interviews/{interviewSessionId}/threads']")
                        .doesNotExist());
    }

    @Test
    void productionOpenApiUsesDistinctClientNamesAndEnglishTags() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Set<String> operationIds = new HashSet<>();
        Set<String> apiIds = new HashSet<>();
        Set<String> tags = new HashSet<>();

        document.path("paths").elements().forEachRemaining(path -> path.elements().forEachRemaining(operation -> {
            String operationId = operation.path("operationId").asText();
            assertThat(operationId).matches("[a-z][A-Za-z0-9]*");
            assertThat(operationIds.add(operationId)).as("duplicate operationId %s", operationId).isTrue();
            String apiId = operation.path("x-rewrite-api-id").asText();
            assertThat(apiId).matches("API-\\d{3}");
            assertThat(apiIds.add(apiId)).as("duplicate API ID %s", apiId).isTrue();
            tags.add(operation.path("tags").get(0).asText());
        }));

        assertThat(operationIds).hasSize(29);
        assertThat(apiIds).hasSize(29).doesNotContain("API-028");
        assertThat(tags).containsExactlyInAnyOrder(
                "Auth", "CoverLetters", "ReviewVersions", "LLMJobs", "KeywordAnalysis", "Interviews");
    }

    @Test
    void openApiDocumentProvidesRequestExamplesAndConstraints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.title.example")
                        .value("2026 상반기 Rewrite 백엔드 개발자 자기소개서"))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.title.maxLength")
                        .value(50))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.companyName.maxLength")
                        .value(30))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.positionTitle.maxLength")
                        .value(30))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.jobPostingUrl.example")
                        .value("https://recruit.example.com/jobs/backend-developer"))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.jobPostingUrl.maxLength")
                        .value(500))
                .andExpect(jsonPath("$.components.schemas.SavePreferencesRequest.properties.preferences.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SavePreferencesRequest.properties.preferences.maxLength")
                        .value(3000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.question.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.question.maxLength")
                        .value(300))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.example")
                        .value(1000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.minimum")
                        .value(100))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.maximum")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.originalAnswer.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.originalAnswer.maxLength")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.RequestReReviewRequest.properties.requestInstruction.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.RequestReReviewRequest.properties.requestInstruction.maxLength")
                        .value(1000))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.required")
                        .value(containsInAnyOrder("questionResultId", "finalAnswer")))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.questionResultId.example")
                        .value("rvqr_123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.maxLength")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.required[0]")
                        .value("content"))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.maxLength")
                        .value(2000));
    }

    @Test
    void publicDocumentationAccessDoesNotOpenApplicationApi() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void publicClassificationFieldsReferenceReusableEnums() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode schemas = document.path("components").path("schemas");
        Map<String, List<String>> expectedValues = Map.ofEntries(
                Map.entry("AuthProvider", List.of("KAKAO")),
                Map.entry("LatestKeywordAnalysisStatus", List.of("NOT_STARTED", "PROCESSING", "COMPLETED", "FAILED")),
                Map.entry("KeywordAnalysisStatus", List.of("PROCESSING", "COMPLETED", "FAILED")),
                Map.entry("LlmJobStatus", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELED")),
                Map.entry("LlmJobType", List.of("COVER_LETTER_REVIEW", "COVER_LETTER_RE_REVIEW", "KEYWORD_ANALYSIS",
                        "INTERVIEW_INITIAL_QUESTION_GENERATION", "INTERVIEW_ADDITIONAL_QUESTION_GENERATION", "INTERVIEW_MESSAGE_FEEDBACK")),
                Map.entry("LlmJobResultRefType", List.of("REVIEW_VERSION", "KEYWORD_ANALYSIS", "INTERVIEW_SESSION", "INTERVIEW_QUESTION", "INTERVIEW_MESSAGE")),
                Map.entry("LlmJobErrorCode", List.of("LLM_PROVIDER_ERROR", "LLM_CONTEXT_LENGTH_EXCEEDED", "LLM_CONTENT_FILTERED")),
                Map.entry("ErrorCode", List.of("UNAUTHORIZED", "CSRF_TOKEN_INVALID", "NOT_FOUND", "VALIDATION_ERROR", "CONFLICT",
                        "COVER_LETTER_NOT_WRITING", "LLM_JOB_ALREADY_RUNNING", "REVIEW_VERSION_NOT_LATEST", "INTERNAL_ERROR")),
                Map.entry("CoverLetterStatus", List.of("WRITING", "REVIEWING", "REVIEWED", "REVIEW_FAILED")),
                Map.entry("InterviewSessionStatus", List.of("QUESTION_GENERATING", "ACTIVE", "FAILED")),
                Map.entry("InterviewMessageRole", List.of("USER", "ASSISTANT")),
                Map.entry("OAuthRedirectErrorCode", List.of("KAKAO_LOGIN_CANCELED", "KAKAO_LOGIN_FAILED")),
                Map.entry("SseEventName", List.of("job.state", "review.questions", "interview.feedback.delta",
                        "cover-letter.review-status.snapshot", "cover-letter.review-status.changed", "heartbeat"))
        );
        expectedValues.forEach((name, values) -> assertThat(schemas.path(name).path("enum")
                .valueStream().map(JsonNode::asText).toList()).as("public values of %s", name)
                .containsExactlyInAnyOrderElementsOf(values));

        Map<String, String> fields = Map.ofEntries(
                Map.entry("CurrentUserResponse.provider", "AuthProvider"),
                Map.entry("LatestKeywordAnalysisResponse.status", "LatestKeywordAnalysisStatus"),
                Map.entry("StartKeywordAnalysisResponse.status", "KeywordAnalysisStatus"),
                Map.entry("ResultRefResponse.type", "LlmJobResultRefType"),
                Map.entry("ErrorBody.code", "ErrorCode"),
                Map.entry("LlmJobErrorResponse.code", "LlmJobErrorCode"),
                Map.entry("CoverLetterDetailJobErrorResponse.code", "LlmJobErrorCode"),
                Map.entry("LlmJobStateResponse.status", "LlmJobStatus"),
                Map.entry("ReviewJobResponse.status", "LlmJobStatus"),
                Map.entry("ReviewVersionListItemResponse.status", "LlmJobStatus"),
                Map.entry("CoverLetterDetailReviewVersionResponse.status", "LlmJobStatus"),
                Map.entry("CoverLetterListItemResponse.displayStatus", "CoverLetterStatus"),
                Map.entry("CoverLetterDetailCoverLetterResponse.displayStatus", "CoverLetterStatus"),
                Map.entry("SubmitCoverLetterResponse.displayStatus", "CoverLetterStatus"),
                Map.entry("RequestReReviewResponse.displayStatus", "CoverLetterStatus"),
                Map.entry("StartInterviewResponse.status", "InterviewSessionStatus"),
                Map.entry("InterviewSessionResponse.status", "InterviewSessionStatus"),
                Map.entry("InterviewMessageResponse.role", "InterviewMessageRole"),
                Map.entry("LlmJobStateEventResponse.jobType", "LlmJobType"),
                Map.entry("LlmJobStateEventResponse.status", "LlmJobStatus"),
                Map.entry("CoverLetterReviewStatusItem.displayStatus", "CoverLetterStatus")
        );
        fields.forEach((field, type) -> {
            String[] parts = field.split("\\.", 2);
            assertThat(schemas.path(parts[0]).path("properties").path(parts[1]).path("$ref").asText())
                    .as("shared enum of %s", field).isEqualTo("#/components/schemas/" + type);
        });
        assertThat(schemas.has("LlmJobTargetType")).isFalse();
        assertThat(schemas.has("LlmJobRequestRefType")).isFalse();
        assertThat(schemas.has("InterviewQuestionType")).isFalse();
        assertThat(schemas.has("InterviewThreadStatus")).isFalse();

        // 클라이언트 타입 생성 검증에 사용할 실제 운영 profile 명세를 build 아래에 보관한다.
        Path exportedDocument = Path.of("build", "verification", "openapi.json");
        Files.createDirectories(exportedDocument.getParent());
        Files.writeString(exportedDocument, document.toPrettyString());
    }

    @Test
    void streamAndRedirectOperationsExposePayloadAndPublicCodeReferences() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode schemas = document.path("components").path("schemas");
        Map<String, String> expectedJob = Map.of(
                "job.state", "LlmJobStateEventResponse",
                "review.questions", "ReviewQuestionsEventResponse",
                "interview.feedback.delta", "InterviewFeedbackDeltaResponse",
                "heartbeat", "SseHeartbeatEvent");
        Map<String, String> expectedCover = Map.of(
                "cover-letter.review-status.snapshot", "CoverLetterReviewStatusSnapshot",
                "cover-letter.review-status.changed", "CoverLetterReviewStatusItem",
                "heartbeat", "SseHeartbeatEvent");
        Map<String, Map<String, String>> streams = Map.of(
                "/llm-jobs/{jobId}/stream", expectedJob,
                "/cover-letters/stream", expectedCover);
        streams.forEach((path, expected) -> {
            JsonNode operation = document.path("paths").path(path).path("get");
            JsonNode mapping = operation.path("x-rewrite-sse-events");
            assertThat(mapping.propertyStream().map(Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrderElementsOf(expected.keySet());
            expected.forEach((event, model) -> {
                assertThat(mapping.path(event).path("$ref").asText()).isEqualTo("#/components/schemas/" + model);
                assertThat(schemas.has(model)).as("payload model %s", model).isTrue();
            });
            assertThat(operation.path("x-rewrite-sse-event-name-schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/SseEventName");
            assertThat(operation.path("responses").path("200").path("content")
                    .path("text/event-stream").path("schema").path("type").asText()).isEqualTo("string");
        });
        assertThat(schemas.path("ReviewQuestionsEventResponse").path("properties").path("items")
                .path("items").path("$ref").asText()).isEqualTo("#/components/schemas/ReviewQuestionEventResponse");
        assertThat(schemas.path("SseHeartbeatEvent").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("SseHeartbeatEvent").path("properties").size()).isZero();
        for (String path : List.of("/auth/kakao/authorize", "/auth/kakao/callback")) {
            JsonNode operation = document.path("paths").path(path).path("get");
            assertThat(operation.path("x-rewrite-redirect-error-code-schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/OAuthRedirectErrorCode");
            assertThat(operation.path("responses").path("302").has("content")).isFalse();
        }
    }

    @Test
    void generatedJsonResponsesMarkEveryReturnedFieldRequiredAndNullableFieldsExplicitly() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        JsonNode components = document.path("components").path("schemas");
        Deque<JsonNode> pending = new ArrayDeque<>();
        Set<String> responseSchemas = new HashSet<>();

        document.path("paths").elements().forEachRemaining(path ->
                path.elements().forEachRemaining(operation ->
                        operation.path("responses").elements().forEachRemaining(response ->
                                response.path("content").elements().forEachRemaining(mediaType -> {
                                    if (mediaType.has("schema")) {
                                        pending.add(mediaType.path("schema"));
                                    }
                                }))));

        document.path("paths").elements().forEachRemaining(path ->
                path.elements().forEachRemaining(operation ->
                        operation.path("x-rewrite-sse-events").elements().forEachRemaining(pending::add)));

        // 생성 문서의 응답 schema에서 출발해 참조·배열·합성 schema를 순회한다. 같은 component는 한 번만 확장한다.
        while (!pending.isEmpty()) {
            JsonNode schema = pending.removeFirst();
            if (schema.has("$ref")) {
                assertThat(isNullable(schema)).as("nullable $ref must use oneOf").isFalse();
                String reference = schema.path("$ref").asText();
                assertThat(reference).startsWith("#/components/schemas/");
                String name = reference.substring("#/components/schemas/".length());
                if (responseSchemas.add(name)) {
                    assertThat(components.has(name)).as("missing schema %s", name).isTrue();
                    pending.add(components.path(name));
                }
            }
            if (schema.has("properties")) {
                Set<String> properties = new HashSet<>();
                schema.path("properties").fieldNames().forEachRemaining(properties::add);
                Set<String> required = new HashSet<>();
                schema.path("required").forEach(item -> required.add(item.asText()));
                assertThat(required).as("response schema required fields").containsExactlyInAnyOrderElementsOf(properties);
                schema.path("properties").elements().forEachRemaining(pending::add);
            }
            if (schema.has("items")) {
                pending.add(schema.path("items"));
            }
            for (String composition : Set.of("oneOf", "anyOf", "allOf")) {
                schema.path(composition).forEach(pending::add);
            }
        }

        // required는 필드 존재 여부이며 nullable은 값의 허용 범위다. 공개 응답의 두 계약을 따로 확인한다.
        Set<String> nullableProperties = new HashSet<>();
        for (String name : responseSchemas) {
            components.path(name).path("properties").propertyStream().forEach(property -> {
                if (isNullable(property.getValue())) {
                    nullableProperties.add(name + "." + property.getKey());
                }
            });
        }
        assertThat(nullableProperties).containsExactlyInAnyOrderElementsOf(expectedNullableResponseProperties());
        assertThat(responseSchemas).contains("ErrorResponse", "ErrorBody", "ErrorDetail", "SuccessResponse",
                "CoverLetterDetailResponse", "LlmJobStateResponse", "LatestKeywordAnalysisResponse");
    }

    private boolean isNullable(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isTextual() && type.asText().equals("null")) {
            return true;
        }
        if (type.isArray() && type.valueStream().anyMatch(item -> item.asText().equals("null"))) {
            return true;
        }
        return schema.path("oneOf").valueStream()
                .anyMatch(item -> item.path("type").asText().equals("null"));
    }

    private Set<String> expectedNullableResponseProperties() {
        return Set.of(
                "CurrentUserResponse.profileImageUrl",
                "CoverLetterListItemResponse.title", "CoverLetterListItemResponse.companyName",
                "CoverLetterListItemResponse.positionTitle", "CoverLetterListItemResponse.latestReviewedVersionId",
                "CoverLetterDetailResponse.reviewVersion", "CoverLetterDetailResponse.reviewJob",
                "CoverLetterDetailCoverLetterResponse.title", "CoverLetterDetailCoverLetterResponse.companyName",
                "CoverLetterDetailCoverLetterResponse.positionTitle", "CoverLetterDetailCoverLetterResponse.jobPostingUrl",
                "CoverLetterDetailCoverLetterResponse.preferences",
                "CoverLetterDetailReviewVersionResponse.requestInstruction", "ReviewJobResponse.error",
                "QuestionResponse.questionResultId", "QuestionResponse.question", "QuestionResponse.maxAnswerLength",
                "QuestionResponse.originalAnswer", "QuestionResponse.originalAnswerLength",
                "QuestionResponse.aiReport", "QuestionResponse.rewrittenAnswer",
                "QuestionResponse.rewrittenAnswerLength", "QuestionResponse.finalAnswer",
                "QuestionResponse.finalAnswerLength",
                "LlmJobStateResponse.resultRef", "LlmJobStateResponse.error",
                "LlmJobStateEventResponse.resultRef", "LlmJobStateEventResponse.error",
                "CoverLetterReviewStatusItem.latestReviewedVersionId",
                "LatestKeywordAnalysisResponse.sourceReviewVersion", "LatestKeywordAnalysisResponse.jobId",
                "KeywordAnalysisCoverLetterResponse.title", "KeywordAnalysisCoverLetterResponse.companyName",
                "KeywordAnalysisCoverLetterResponse.positionTitle",
                "CurrentInterviewResponse.interviewSession", "CurrentInterviewCoverLetterResponse.title",
                "CurrentInterviewCoverLetterResponse.companyName", "CurrentInterviewCoverLetterResponse.positionTitle",
                "InterviewSessionResponse.jobId", "StartInterviewResponse.jobId",
                "InterviewQuestionListResponse.nextCursor", "InterviewMessageListResponse.jobId",
                "InterviewMessageResponse.score", "SubmitCoverLetterResponse.jobId"
        );
    }

}
