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

class OpenAiReviewClientTest {

    @Test
    void reviewsTargetQuestionWithWholeCoverLetterContext() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "questionId": "clq_1",
                  "aiReport": " 첫 번째 리포트 ",
                  "rewrittenAnswer": " 첫 번째 수정본 "
                }
                """);
        ReviewClient client = new OpenAiReviewClient(ChatClient.builder(chatModel));
        ReviewRequest request = request();

        ReviewResult result = client.reviewQuestion(request, "clq_1");

        assertThat(result.questionId()).isEqualTo("clq_1");
        assertThat(result.aiReport()).isEqualTo("첫 번째 리포트");
        assertThat(result.rewrittenAnswer()).isEqualTo("첫 번째 수정본");

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
                )
                .contains("대상 questionId: clq_1");
    }

    @Test
    void rejectsMismatchedOrWrappedResult() {
        List<String> invalidResponses = List.of(
                """
                        {"questionId":"clq_2","aiReport":"리포트","rewrittenAnswer":"수정본"}
                        """,
                """
                        {"results":[
                          {"questionId":"clq_1","aiReport":"리포트1","rewrittenAnswer":"수정본1"},
                          {"questionId":"clq_1","aiReport":"리포트2","rewrittenAnswer":"수정본2"}
                        ]}
                        """
        );

        for (String response : invalidResponses) {
            ReviewClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.reviewQuestion(request(), "clq_1"));
        }
    }

    @Test
    void rejectsBlankOrTooLongGeneratedText() {
        List<String> invalidResponses = List.of(
                """
                        {"questionId":"clq_1","aiReport":" ","rewrittenAnswer":"수정본"}
                        """,
                """
                        {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":" "}
                        """,
                """
                        {"questionId":"clq_1","aiReport":"리포트","rewrittenAnswer":"%s"}
                        """.formatted("가".repeat(1001))
        );

        for (String response : invalidResponses) {
            ReviewClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.reviewQuestion(request(), "clq_1"));
        }
    }

    @Test
    void classifiesMalformedJsonAsOutputValidationFailure() {
        ReviewClient client = clientReturning("not-json");

        assertOutputValidationFailure(() -> client.reviewQuestion(request(), "clq_1"));
    }

    @Test
    void wrapsProviderFailureAndPreservesCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        ChatModel failingModel = prompt -> {
            throw cause;
        };
        ReviewClient client = new OpenAiReviewClient(ChatClient.builder(failingModel));

        assertThatThrownBy(() -> client.reviewQuestion(request(), "clq_1"))
                .isInstanceOfSatisfying(ReviewClientException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(ReviewClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void includesReReviewInstructionInPromptWhenPresent() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "questionId": "clq_1",
                  "aiReport": "리포트",
                  "rewrittenAnswer": "수정본"
                }
                """);
        ReviewClient client = new OpenAiReviewClient(ChatClient.builder(chatModel));

        client.reviewQuestion(new ReviewRequest(
                "백엔드 자기소개서",
                "다온",
                "백엔드 개발자",
                "https://example.com/jobs/1",
                "Spring 경험 우대",
                "직무 키워드를 더 강조해주세요.",
                List.of(new ReviewQuestion("clq_1", 1, "지원 동기는?", 1000, "최종 작성본"))
        ), "clq_1");

        assertThat(chatModel.capturedPrompt().getSystemMessage().getText())
                .contains("재첨삭 요구사항");
        assertThat(chatModel.capturedPrompt().getUserMessage().getText())
                .contains("직무 키워드를 더 강조해주세요.", "최종 작성본");
    }

    private ReviewClient clientReturning(String response) {
        return new OpenAiReviewClient(ChatClient.builder(new CapturingChatModel(response)));
    }

    private void assertOutputValidationFailure(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(ReviewClientException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    private ReviewRequest request() {
        return new ReviewRequest(
                "백엔드 자기소개서",
                "다온",
                "백엔드 개발자",
                "https://example.com/jobs/1",
                "Spring 경험 우대",
                List.of(
                        new ReviewQuestion("clq_1", 1, "지원 동기는?", 1000, "첫 번째 원본 답변"),
                        new ReviewQuestion("clq_2", 2, "직무 역량은?", 500, "두 번째 원본 답변")
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
