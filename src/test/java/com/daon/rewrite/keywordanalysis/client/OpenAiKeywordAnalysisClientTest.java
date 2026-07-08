package com.daon.rewrite.keywordanalysis.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiKeywordAnalysisClientTest {

    @Test
    void analyzesFinalAnswersAndReturnsKeywordsByImportanceDescending() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "keywords": [
                    {
                      "keyword": " Spring ",
                      "importance": 88
                    },
                    {
                      "keyword": "백엔드",
                      "importance": 95
                    }
                  ]
                }
                """);
        KeywordAnalysisClient client = new OpenAiKeywordAnalysisClient(ChatClient.builder(chatModel));

        List<KeywordAnalysisResult> results = client.analyze(request());

        assertThat(results)
                .extracting(KeywordAnalysisResult::keyword, KeywordAnalysisResult::importance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("백엔드", 95),
                        org.assertj.core.groups.Tuple.tuple("Spring", 88)
                );

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText())
                .contains("자기소개서", "핵심 키워드", "상위 20개", "importance", "1~100");
        assertThat(prompt.getUserMessage().getText())
                .contains(
                        "백엔드 자기소개서",
                        "다온",
                        "백엔드 개발자",
                        "Spring 경험 우대",
                        "clq_1",
                        "지원 동기는?",
                        "최종 작성본 1",
                        "clq_2",
                        "직무 역량은?",
                        "최종 작성본 2"
                );
    }

    @Test
    void rejectsInvalidKeywordResponses() {
        List<String> invalidResponses = List.of(
                """
                        {"keywords":null}
                        """,
                """
                        {"keywords":[]}
                        """,
                """
                        {"keywords":[{"keyword":" ","importance":80}]}
                        """,
                """
                        {"keywords":[{"keyword":"백엔드","importance":0}]}
                        """,
                """
                        {"keywords":[{"keyword":"백엔드","importance":101}]}
                        """,
                """
                        {"keywords":[
                          {"keyword":"백엔드","importance":95},
                          {"keyword":" 백엔드 ","importance":90}
                        ]}
                        """,
                """
                        {"keywords":[%s]}
                        """.formatted("\"" + "{\"keyword\":\"키워드\",\"importance\":80}" + "\"")
        );

        for (String response : invalidResponses) {
            KeywordAnalysisClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.analyze(request()));
        }
    }

    @Test
    void trimsMoreThanTwentyKeywordsToTopTwentyByImportance() {
        StringBuilder keywordJson = new StringBuilder();
        for (int index = 1; index <= 21; index++) {
            if (index > 1) {
                keywordJson.append(",");
            }
            keywordJson.append("""
                    {"keyword":"키워드%s","importance":%s}
                    """.formatted(index, index));
        }

        KeywordAnalysisClient client = clientReturning("""
                {"keywords":[%s]}
                """.formatted(keywordJson));

        List<KeywordAnalysisResult> results = client.analyze(request());

        assertThat(results).hasSize(20);
        assertThat(results)
                .extracting(KeywordAnalysisResult::keyword, KeywordAnalysisResult::importance)
                .startsWith(org.assertj.core.groups.Tuple.tuple("키워드21", 21))
                .endsWith(org.assertj.core.groups.Tuple.tuple("키워드2", 2));
    }

    @Test
    void classifiesMalformedJsonAsOutputValidationFailure() {
        KeywordAnalysisClient client = clientReturning("not-json");

        assertOutputValidationFailure(() -> client.analyze(request()));
    }

    @Test
    void wrapsProviderFailureAndPreservesCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        ChatModel failingModel = prompt -> {
            throw cause;
        };
        KeywordAnalysisClient client = new OpenAiKeywordAnalysisClient(ChatClient.builder(failingModel));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOfSatisfying(KeywordAnalysisClientException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(KeywordAnalysisClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private KeywordAnalysisClient clientReturning(String response) {
        return new OpenAiKeywordAnalysisClient(ChatClient.builder(new CapturingChatModel(response)));
    }

    private void assertOutputValidationFailure(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(KeywordAnalysisClientException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(KeywordAnalysisClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    private KeywordAnalysisRequest request() {
        return new KeywordAnalysisRequest(
                "백엔드 자기소개서",
                "다온",
                "백엔드 개발자",
                "Spring 경험 우대",
                List.of(
                        new KeywordAnalysisAnswer("clq_1", 1, "지원 동기는?", "최종 작성본 1"),
                        new KeywordAnalysisAnswer("clq_2", 2, "직무 역량은?", "최종 작성본 2")
                )
        );
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void invoke();
    }

    private static final class CapturingChatModel implements ChatModel {

        private final String response;
        private Prompt capturedPrompt;

        private CapturingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.capturedPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }

        private Prompt capturedPrompt() {
            return capturedPrompt;
        }
    }
}
