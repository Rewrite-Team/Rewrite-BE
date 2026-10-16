package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.coverletter.dto.CoverLetterReviewStatusEventResponse;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.llmjob.dto.InterviewFeedbackDeltaResponse;
import com.daon.rewrite.llmjob.dto.LlmJobStateEventResponse;
import com.daon.rewrite.llmjob.dto.ReviewQuestionsEventResponse;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * test·auth-dev에서 실제 생성한 OpenAPI의 문서 메타데이터·오류 예제·첨삭 상태 계약과 Swagger 접근을 검증한다.
 * auth-real에서 활성화되는 인증 API까지 포함한 전체 계약과 문서 접근 보안은 OpenApiSecurityIntegrationTest가 확인한다.
 */
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
        assertThat(api009.path("operationId").asText()).isEqualTo("saveCoverLetterBasicInfo");
        assertThat(api009.path("summary").asText()).isEqualTo("API-009 · 기본 정보 저장");
        assertThat(api009.path("tags").get(0).asText()).isEqualTo("CoverLetters");
        assertThat(api009.path("x-rewrite-api-id").asText()).isEqualTo("API-009");
        assertThat(api009.path("x-rewrite-screens").get(0).asText())
                .isEqualTo("자기소개서 등록");
        assertThat(api009.path("x-rewrite-authenticated").asBoolean()).isTrue();
        assertThat(api009.path("x-rewrite-csrf-protected").asBoolean()).isTrue();
        assertThat(api009.path("description").asText())
                .contains("### 사용 목적", "### 사용 화면", "### 호출 시점", "### 주요 동작", "### 성공 후 처리", "### 오류");

        JsonNode api014 = operation(document, "/cover-letters/{coverLetterId}/submit", "post");
        assertThat(api014.path("operationId").asText()).isEqualTo("submitCoverLetter");
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
        assertThat(api016.path("operationId").asText()).isEqualTo("streamLlmJobEvents");
        assertThat(api016.path("x-rewrite-screens").valueStream().map(JsonNode::asText).toList())
                .containsExactly("첨삭 진행", "키워드 분석", "AI 면접");
        assertThat(api016.path("x-rewrite-csrf-protected").asBoolean()).isFalse();
        assertThat(api016.path("description").asText())
                .contains(
                        "job.state.status=FAILED",
                        "HTTP ErrorResponse가 아니다",
                        "최종 job.state와 도메인별 후속 이벤트 전송을 마친 뒤 연결을 종료",
                        "기본 15초", "heartbeat", "기본 30초 무응답", "API-015"
                );
        assertThat(api016.path("responses").has("403")).isFalse();
        assertThat(api016.path("responses").path("200").path("content")
                .path("text/event-stream").path("examples").path("coverLetterReview").path("value").asText())
                .contains("event: job.state", "event: review.questions", "event: heartbeat\ndata: {}");

        JsonNode api030 = operation(document, "/cover-letters/stream", "get");
        assertThat(api030.path("responses").path("200").path("content")
                .path("text/event-stream").path("schema").path("example").asText())
                .contains("cover-letter.review-status.snapshot", "cover-letter.review-status.changed",
                        "event: heartbeat\ndata: {}")
                .doesNotContain("\"jobId\"");
        assertThat(api030.path("description").asText())
                .contains("기본 15초", "heartbeat", "기본 30초 무응답", "API-007");
    }

    @Test
    void jobStreamExamplesDocumentEveryJobTypeAndItsCompletedResult() throws Exception {
        JsonNode api016 = operation(openApiDocument(), "/llm-jobs/{jobId}/stream", "get");
        JsonNode streamContent = api016.path("responses").path("200").path("content").path("text/event-stream");
        assertThat(streamContent.path("schema").path("type").asText()).isEqualTo("string");
        JsonNode examples = streamContent.path("examples");
        Map<String, LlmJobType> expectedTypes = Map.of(
                "coverLetterReview", LlmJobType.COVER_LETTER_REVIEW,
                "coverLetterReReview", LlmJobType.COVER_LETTER_RE_REVIEW,
                "keywordAnalysis", LlmJobType.KEYWORD_ANALYSIS,
                "interviewInitialQuestionGeneration", LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION,
                "interviewAdditionalQuestionGeneration", LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION,
                "interviewMessageFeedback", LlmJobType.INTERVIEW_MESSAGE_FEEDBACK
        );
        assertThat(expectedTypes.values()).containsExactlyInAnyOrder(LlmJobType.values());
        assertThat(examples.propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrderElementsOf(expectedTypes.keySet());
        String description = api016.path("description").asText();
        assertThat(fencedCodeBlocks(description)).containsExactlyInAnyOrderElementsOf(
                examples.propertyStream().map(entry -> entry.getValue().path("value").asText()).toList()
        );

        for (Map.Entry<String, LlmJobType> expected : expectedTypes.entrySet()) {
            List<SseEvent> events = readSseEvents(examples.path(expected.getKey()).path("value").asText());
            assertThat(events.getFirst().name()).isEqualTo("job.state");
            assertThat(events.getLast().name()).isEqualTo("job.state");
            List<LlmJobStateEventResponse> states = new ArrayList<>();
            for (JsonNode payload : eventPayloads(events, "job.state")) {
                states.add(readPayload(payload, LlmJobStateEventResponse.class));
            }
            assertThat(states).extracting(LlmJobStateEventResponse::status)
                    .containsExactly(LlmJobStatus.PROCESSING, LlmJobStatus.COMPLETED);
            int total = expected.getValue() == LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION ? 5 : 1;
            assertThat(states).allSatisfy(state -> {
                assertThat(state.jobType()).isEqualTo(expected.getValue());
                assertThat(state.progress()).isNotNull();
                assertThat(state.progress().total()).isEqualTo(total);
                assertThat(state.progress().message()).isNotBlank();
                assertThat(state.error()).isNull();
            });
            assertThat(states.getFirst().progress().current()).isZero();
            assertThat(states.getFirst().resultRef()).isNull();
            LlmJobStateEventResponse completed = states.getLast();
            assertThat(completed.progress().current()).isEqualTo(total);
            assertThat(completed.resultRef()).isNotNull();
            LlmJobResultRefType expectedResult = switch (expected.getValue()) {
                case COVER_LETTER_REVIEW, COVER_LETTER_RE_REVIEW -> LlmJobResultRefType.REVIEW_VERSION;
                case KEYWORD_ANALYSIS -> LlmJobResultRefType.KEYWORD_ANALYSIS;
                case INTERVIEW_INITIAL_QUESTION_GENERATION -> LlmJobResultRefType.INTERVIEW_SESSION;
                case INTERVIEW_ADDITIONAL_QUESTION_GENERATION -> LlmJobResultRefType.INTERVIEW_QUESTION;
                case INTERVIEW_MESSAGE_FEEDBACK -> LlmJobResultRefType.INTERVIEW_MESSAGE;
            };
            assertThat(completed.resultRef().type()).isEqualTo(expectedResult);
            assertThat(completed.resultRef().id()).isNotBlank();
            assertThat(eventPayloads(events, "heartbeat")).singleElement().satisfies(payload -> {
                assertThat(payload.isObject()).isTrue();
                assertThat(payload.isEmpty()).isTrue();
            });
        }
    }

    @Test
    void jobStreamExamplesSeparateReviewQuestionsAndValidatedInterviewFeedback() throws Exception {
        JsonNode api016 = operation(openApiDocument(), "/llm-jobs/{jobId}/stream", "get");
        JsonNode examples = api016.path("responses").path("200").path("content")
                .path("text/event-stream").path("examples");
        for (Map.Entry<String, JsonNode> example : examples.propertyStream().toList()) {
            List<SseEvent> events = readSseEvents(example.getValue().path("value").asText());
            List<JsonNode> reviews = eventPayloads(events, "review.questions");
            if (Set.of("coverLetterReview", "coverLetterReReview").contains(example.getKey())) {
                assertThat(events.get(1).name()).isEqualTo("review.questions");
                assertThat(reviews).hasSize(2);
                assertThat(readPayload(reviews.getFirst(), ReviewQuestionsEventResponse.class).items()).isEmpty();
                ReviewQuestionsEventResponse completedQuestion = readPayload(
                        reviews.getLast(), ReviewQuestionsEventResponse.class
                );
                assertThat(completedQuestion.items()).singleElement().satisfies(question -> {
                    assertThat(question.questionId()).isNotBlank();
                    assertThat(question.order()).isPositive();
                    assertThat(question.aiReport()).isNotBlank();
                    assertThat(question.rewrittenAnswer()).isNotBlank();
                    assertThat(question.rewrittenAnswerLength()).isEqualTo(question.rewrittenAnswer().length());
                    assertThat(question.finalAnswer()).isNotBlank();
                    assertThat(question.finalAnswerLength()).isEqualTo(question.finalAnswer().length());
                });
            } else {
                assertThat(reviews).isEmpty();
            }

            List<InterviewFeedbackDeltaResponse> deltas = new ArrayList<>();
            for (JsonNode payload : eventPayloads(events, "interview.feedback.delta")) {
                deltas.add(readPayload(payload, InterviewFeedbackDeltaResponse.class));
            }
            if (example.getKey().equals("interviewMessageFeedback")) {
                assertThat(deltas).extracting(InterviewFeedbackDeltaResponse::sequence).containsExactly(1, 2);
                assertThat(deltas).extracting(InterviewFeedbackDeltaResponse::contentDelta)
                        .allSatisfy(delta -> assertThat(delta).isNotBlank());
            } else {
                assertThat(deltas).isEmpty();
            }
        }

        JsonNode feedbackExample = examples.path("interviewMessageFeedback");
        assertThat(feedbackExample.path("summary").asText()).isEqualTo("면접 답변 피드백 상태와 조각");
        assertThat(feedbackExample.path("description").asText())
                .contains("전체 AI 응답을 검증한 뒤", "sequence 순서로 contentDelta를 이어 붙인다");
        assertThat(api016.path("description").asText())
                .contains("전체 AI 응답을 검증한 뒤 완성된 content");
    }

    @Test
    void coverLetterStreamDocumentsItsDtoPayloadsAndAllPublicSseEvents() throws Exception {
        JsonNode document = openApiDocument();
        JsonNode api030 = operation(document, "/cover-letters/stream", "get");
        JsonNode schema = api030.path("responses").path("200").path("content")
                .path("text/event-stream").path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("string");
        List<String> codeBlocks = fencedCodeBlocks(api030.path("description").asText());
        assertThat(codeBlocks).hasSize(1);
        String stream = codeBlocks.getFirst();
        assertThat(schema.path("example").asText().stripTrailing()).isEqualTo(stream.stripTrailing());
        List<SseEvent> events = readSseEvents(stream);
        assertThat(events).extracting(SseEvent::name).containsExactly(
                "cover-letter.review-status.snapshot", "cover-letter.review-status.changed", "heartbeat"
        );
        CoverLetterReviewStatusEventResponse.Snapshot snapshot = readPayload(
                events.getFirst().payload(), CoverLetterReviewStatusEventResponse.Snapshot.class
        );
        assertThat(snapshot.items()).singleElement().satisfies(item -> {
            assertThat(item.coverLetterId()).isNotBlank();
            assertThat(item.displayStatus()).isEqualTo(CoverLetterStatus.REVIEWING);
            assertThat(item.latestReviewedVersionId()).isNull();
        });
        CoverLetterReviewStatusEventResponse.Item changed = readPayload(
                events.get(1).payload(), CoverLetterReviewStatusEventResponse.Item.class
        );
        assertThat(changed.coverLetterId()).isEqualTo(snapshot.items().getFirst().coverLetterId());
        assertThat(changed.displayStatus()).isEqualTo(CoverLetterStatus.REVIEWED);
        assertThat(changed.latestReviewedVersionId()).isNotBlank();
        assertThat(eventPayloads(events, "heartbeat")).singleElement().satisfies(payload -> {
            assertThat(payload.isObject()).isTrue();
            assertThat(payload.isEmpty()).isTrue();
        });

        Set<String> eventNames = new HashSet<>();
        events.forEach(event -> eventNames.add(event.name()));
        JsonNode jobExamples = operation(document, "/llm-jobs/{jobId}/stream", "get")
                .path("responses").path("200").path("content").path("text/event-stream").path("examples");
        for (JsonNode example : jobExamples) {
            readSseEvents(example.path("value").asText()).forEach(event -> eventNames.add(event.name()));
        }
        assertThat(eventNames).containsExactlyInAnyOrder(
                "job.state", "review.questions", "interview.feedback.delta", "heartbeat",
                "cover-letter.review-status.snapshot", "cover-letter.review-status.changed"
        );
    }

    @Test
    void reviewVersionApisDocumentAttemptStatusAndLatestSuccessfulResult() throws Exception {
        JsonNode document = openApiDocument();
        JsonNode schemas = document.path("components").path("schemas");

        for (String schemaName : List.of("ReviewVersionListItemResponse", "CoverLetterDetailReviewVersionResponse")) {
            JsonNode schema = schemas.path(schemaName);
            assertThat(schema.isMissingNode()).isFalse();
            assertThat(schema.path("required").valueStream().map(JsonNode::asText).toList())
                    .contains("status", "isLatest", "isLatestReviewed");
            assertThat(schema.path("properties").has("status")).isTrue();
        }
        for (String schemaName : List.of("ReviewVersionListItemResponse", "CoverLetterDetailReviewVersionResponse",
                "KeywordAnalysisReviewVersionResponse")) {
            JsonNode schema = schemas.path(schemaName);
            assertThat(schema.isMissingNode()).isFalse();
            assertThat(schema.path("required").valueStream().map(JsonNode::asText).toList()).contains("version");
            assertThat(schema.path("properties").path("version").path("type").asText()).isEqualTo("string");
            assertThat(schema.path("properties").has("versionNumber")).isFalse();
        }

        assertThat(operation(document, "/cover-letters/{coverLetterId}/review-versions", "get")
                .path("description").asText())
                .contains("진행·성공·실패 버전", "isLatestReviewed");
        assertThat(operation(document, "/cover-letters/{coverLetterId}/review-versions/{versionId}", "get")
                .path("description").asText())
                .contains("진행·실패 버전", "부분 결과");
        assertThat(operation(document, "/cover-letters/{coverLetterId}/submit", "post")
                .path("description").asText())
                .contains("Job과 첨삭 버전을 함께");
    }

    @Test
    void documentedErrorsUseSharedSchemaAndNamedExamples() throws Exception {
        JsonNode document = openApiDocument();
        assertThat(document.path("components").path("schemas").path("SuccessResponse")
                .path("required").valueStream().map(JsonNode::asText).toList())
                .contains("success");
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
    void rewriteApiOperationIdsAreUniqueAndSeparateFromApiIds() throws Exception {
        JsonNode paths = openApiDocument().path("paths");
        List<String> operationIds = new ArrayList<>();
        List<String> apiIds = new ArrayList<>();

        Iterator<JsonNode> pathItems = paths.elements();
        while (pathItems.hasNext()) {
            pathItems.next().elements().forEachRemaining(operation -> {
                if (operation.has("x-rewrite-api-id")) {
                    operationIds.add(operation.path("operationId").asText());
                    apiIds.add(operation.path("x-rewrite-api-id").asText());
                }
            });
        }

        assertThat(operationIds).hasSize(24);
        assertThat(new HashSet<>(operationIds)).hasSameSizeAs(operationIds);
        assertThat(operationIds).allMatch(operationId -> operationId.matches("[a-z][A-Za-z0-9]*"));
        assertThat(apiIds).allMatch(apiId -> apiId.matches("API-\\d{3}"));
        assertThat(new HashSet<>(apiIds)).hasSameSizeAs(apiIds);
        assertThat(apiIds).doesNotContain("API-028");
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

    private List<String> fencedCodeBlocks(String description) {
        return Pattern.compile("(?ms)^```[^\\n]*\\n(.*?)^```[ \\t]*(?=\\n|$)")
                .matcher(description).results().map(match -> match.group(1)).toList();
    }

    private List<SseEvent> readSseEvents(String stream) throws Exception {
        assertThat(stream).startsWith("event: ").endsWith("\n\n");
        List<SseEvent> events = new ArrayList<>();
        for (String frame : stream.split("\n\n")) {
            List<String> lines = frame.lines().toList();
            assertThat(lines).hasSize(2);
            assertThat(lines.getFirst()).startsWith("event: ");
            assertThat(lines.get(1)).startsWith("data: ");
            events.add(new SseEvent(
                    lines.getFirst().substring("event: ".length()),
                    objectMapper.readTree(lines.get(1).substring("data: ".length()))
            ));
        }
        return events;
    }

    private List<JsonNode> eventPayloads(List<SseEvent> events, String eventName) {
        return events.stream().filter(event -> event.name().equals(eventName)).map(SseEvent::payload).toList();
    }

    private <T> T readPayload(JsonNode payload, Class<T> responseType) throws Exception {
        T response = objectMapper.treeToValue(payload, responseType);
        JsonNode serialized = objectMapper.valueToTree(response);
        assertThat(serialized).isEqualTo(payload);
        return response;
    }

    private JsonNode operation(JsonNode document, String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    private record SseEvent(String name, JsonNode payload) {
    }
}
