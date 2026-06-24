package com.daon.rewrite.reviewversion.client;

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

class OpenAiFirstReviewClientTest {

    @Test
    void reviewsWholeCoverLetterAndReturnsResultsInQuestionOrder() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "results": [
                    {
                      "questionId": "clq_2",
                      "aiReport": " 두 번째 리포트 ",
                      "rewrittenAnswer": " 두 번째 수정본 "
                    },
                    {
                      "questionId": "clq_1",
                      "aiReport": " 첫 번째 리포트 ",
                      "rewrittenAnswer": " 첫 번째 수정본 "
                    }
                  ]
                }
                """);
        FirstReviewClient client = new OpenAiFirstReviewClient(ChatClient.builder(chatModel));
        FirstReviewRequest request = request();

        List<FirstReviewResult> results = client.review(request);

        assertThat(results)
                .extracting(FirstReviewResult::questionId)
                .containsExactly("clq_1", "clq_2");
        assertThat(results)
                .extracting(FirstReviewResult::aiReport)
                .containsExactly("첫 번째 리포트", "두 번째 리포트");
        assertThat(results)
                .extracting(FirstReviewResult::rewrittenAnswer)
                .containsExactly("첫 번째 수정본", "두 번째 수정본");

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText())
                .contains("STAR", "구체성", "우대사항", "직무", "맞춤법", "중복 표현", "최대 글자 수")
                .contains("원문에 없는 경험이나 사실을 임의로 만들지");
        assertThat(prompt.getUserMessage().getText())
                .contains(
                        "백엔드 자기소개서",
                        "다온",
                        "백엔드 개발자",
                        "https://example.com/jobs/1",
                        "Spring 경험 우대",
                        "clq_1",
                        "지원 동기는?",
                        "1000",
                        "첫 번째 원본 답변",
                        "clq_2",
                        "직무 역량은?",
                        "500",
                        "두 번째 원본 답변"
                );
    }

    @Test
    void rejectsMissingDuplicateOrUnknownQuestionResults() {
        List<String> invalidResponses = List.of(
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":"수정본"}
                        ]}
                        """,
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트1","rewrittenAnswer":"수정본1"},
                          {"questionId":"clq_1","aiReport":"리포트2","rewrittenAnswer":"수정본2"}
                        ]}
                        """,
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":"수정본"},
                          {"questionId":"clq_unknown","aiReport":"리포트","rewrittenAnswer":"수정본"}
                        ]}
                        """
        );

        for (String response : invalidResponses) {
            FirstReviewClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.review(request()));
        }
    }

    @Test
    void rejectsBlankOrTooLongGeneratedText() {
        List<String> invalidResponses = List.of(
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":" ","rewrittenAnswer":"수정본"},
                          {"questionId":"clq_2","aiReport":"리포트","rewrittenAnswer":"수정본"}
                        ]}
                        """,
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":"수정본"},
                          {"questionId":"clq_2","aiReport":"리포트","rewrittenAnswer":" "}
                        ]}
                        """,
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":"수정본"},
                          {"questionId":"clq_2","aiReport":"리포트","rewrittenAnswer":"%s"}
                        ]}
                        """.formatted("가".repeat(501))
        );

        for (String response : invalidResponses) {
            FirstReviewClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.review(request()));
        }
    }

    @Test
    void classifiesMalformedJsonAsOutputValidationFailure() {
        FirstReviewClient client = clientReturning("not-json");

        assertOutputValidationFailure(() -> client.review(request()));
    }

    @Test
    void wrapsProviderFailureAndPreservesCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        ChatModel failingModel = prompt -> {
            throw cause;
        };
        FirstReviewClient client = new OpenAiFirstReviewClient(ChatClient.builder(failingModel));

        assertThatThrownBy(() -> client.review(request()))
                .isInstanceOfSatisfying(FirstReviewClientException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(FirstReviewClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private FirstReviewClient clientReturning(String response) {
        return new OpenAiFirstReviewClient(ChatClient.builder(new CapturingChatModel(response)));
    }

    private void assertOutputValidationFailure(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(FirstReviewClientException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(FirstReviewClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    private FirstReviewRequest request() {
        return new FirstReviewRequest(
                "백엔드 자기소개서",
                "다온",
                "백엔드 개발자",
                "https://example.com/jobs/1",
                "Spring 경험 우대",
                List.of(
                        new FirstReviewQuestion("clq_1", 1, "지원 동기는?", 1000, "첫 번째 원본 답변"),
                        new FirstReviewQuestion("clq_2", 2, "직무 역량은?", 500, "두 번째 원본 답변")
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
