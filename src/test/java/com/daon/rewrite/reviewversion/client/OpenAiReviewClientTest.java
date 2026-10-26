package com.daon.rewrite.reviewversion.client;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 실제 ChatClient를 통해 구조화 출력 요청과 응답 변환·내용 검증을 외부 호출 없이 확인한다. */
class OpenAiReviewClientTest {

    private static final String QUESTION_ID = "question-3";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private ChatModel model;
    private OpenAiReviewClient client;

    @BeforeEach
    void setUp() {
        model = mock(ChatModel.class);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder()
                .model("gpt-5-nano")
                .temperature(1.0)
                .build());
        client = new OpenAiReviewClient(ChatClient.builder(model));
    }

    @Test
    void sendsReviewSchemaAsNativeOpenAiResponseFormat() {
        respondWith(new ReviewResult(QUESTION_ID, "수정 이유", "수정된 자기소개서"));

        client.reviewQuestion(request(), QUESTION_ID);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-5-nano");
        assertThat(options.getTemperature()).isEqualTo(1.0);
        assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        JsonNode schema = JSON_MAPPER.readTree(options.getOutputSchema());
        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(JSON_MAPPER.treeToValue(schema.path("required"), String[].class))
                .containsExactlyInAnyOrder("questionId", "aiReport", "rewrittenAnswer");
        assertThat(schema.path("properties").path("rewrittenAnswer").has("maxLength")).isFalse();
    }

    @Test
    void enablesStrictJsonSchemaInActualOpenAiSdkRequest() {
        OpenAIClient provider = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(provider.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException("stop before external call"));
        OpenAiChatModel openAiModel = OpenAiChatModel.builder()
                .openAiClient(provider)
                .openAiClientAsync(mock(OpenAIClientAsync.class))
                .options(OpenAiChatOptions.builder().model("gpt-5-nano").temperature(1.0).build())
                .build();
        OpenAiReviewClient openAiClient = new OpenAiReviewClient(ChatClient.builder(openAiModel));

        assertThatThrownBy(() -> openAiClient.reviewQuestion(request(), QUESTION_ID))
                .isInstanceOf(ReviewClientException.class);

        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(provider.chat().completions()).create(params.capture());
        var jsonSchema = params.getValue().responseFormat().orElseThrow().jsonSchema().orElseThrow().jsonSchema();
        assertThat(jsonSchema.strict()).contains(true);
        assertThat(jsonSchema.schema()).isPresent();
    }

    @Test
    void returnsAnswerAboveQuestionTargetWithoutTruncation() {
        String answer = "가".repeat(1072);
        respondWith(new ReviewResult(QUESTION_ID, "수정 이유", answer));

        ReviewResult result = client.reviewQuestion(request(), QUESTION_ID);

        assertThat(result.rewrittenAnswer()).isEqualTo(answer);
    }

    @Test
    void normalizesRequiredTextWhilePreservingQuotesAndParagraphs() {
        respondWith(new ReviewResult(QUESTION_ID, "  문장을 자연스럽게 수정했습니다.\n ",
                " \n[사용자의 행동에서 개선의 근거를 찾다]\n\nCTA를 \"리포트 보기\"로 수정했습니다.\n "));

        ReviewResult result = client.reviewQuestion(request(), QUESTION_ID);

        assertThat(result.aiReport()).isEqualTo("문장을 자연스럽게 수정했습니다.");
        assertThat(result.rewrittenAnswer()).isEqualTo(
                "[사용자의 행동에서 개선의 근거를 찾다]\n\nCTA를 \"리포트 보기\"로 수정했습니다.");
    }

    @ParameterizedTest
    @MethodSource("invalidResults")
    void rejectsMissingContentOrWrongQuestion(ReviewResult response) {
        respondWith(response);

        assertThatThrownBy(() -> client.reviewQuestion(request(), QUESTION_ID))
                .isInstanceOfSatisfying(ReviewClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    @Test
    void rejectsResultForQuestionOutsideRequest() {
        respondWith(new ReviewResult("unknown", "수정 이유", "수정본"));

        assertThatThrownBy(() -> client.reviewQuestion(request(), "unknown"))
                .isInstanceOfSatisfying(ReviewClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    @Test
    void rejectsMalformedJsonEvenWhenNativeOutputWasRequested() {
        String malformed = JSON_MAPPER.writeValueAsString(new ReviewResult(QUESTION_ID, "수정 이유", "수정본")) + "]";
        when(model.call(any(Prompt.class))).thenReturn(chatResponse(malformed));

        assertThatThrownBy(() -> client.reviewQuestion(request(), QUESTION_ID))
                .isInstanceOfSatisfying(ReviewClientException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED);
                    assertThat(exception.getCause()).isInstanceOf(JacksonException.class);
                });
    }

    @Test
    void rejectsResponseWithoutGeneratedContent() {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertThatThrownBy(() -> client.reviewQuestion(request(), QUESTION_ID))
                .isInstanceOfSatisfying(ReviewClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    @Test
    void retainsProviderFailureClassificationAndCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        when(model.call(any(Prompt.class))).thenThrow(cause);

        assertThatThrownBy(() -> client.reviewQuestion(request(), QUESTION_ID))
                .isInstanceOfSatisfying(ReviewClientException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(ReviewClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private static Stream<ReviewResult> invalidResults() {
        return Stream.of(
                null,
                new ReviewResult("different-question", "수정 이유", "수정본"),
                new ReviewResult(QUESTION_ID, null, "수정본"),
                new ReviewResult(QUESTION_ID, " \n ", "수정본"),
                new ReviewResult(QUESTION_ID, "수정 이유", null),
                new ReviewResult(QUESTION_ID, "수정 이유", " \n ")
        );
    }

    private void respondWith(ReviewResult result) {
        when(model.call(any(Prompt.class))).thenReturn(chatResponse(JSON_MAPPER.writeValueAsString(result)));
    }

    private static ChatResponse chatResponse(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private static ReviewRequest request() {
        return new ReviewRequest("자기소개서", "지원 회사", "프론트엔드 개발자", null, null,
                List.of(new ReviewQuestion(QUESTION_ID, 3, "사용자 경험을 개선한 경험", 1000, "가".repeat(1113))));
    }
}
