package com.daon.rewrite.interview.client;

import com.daon.rewrite.interview.entity.InterviewMessageRole;
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

class OpenAiInterviewMessageFeedbackClientTest {

    @Test
    void generatesAndNormalizesFeedbackUsingOriginalQuestionAndConversationHistory() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "content": " 역할과 성과를 연결해 설명한 점이 좋습니다. 이어서 성과를 측정한 기준을 설명해 주세요. ",
                  "feedback": {
                    "summary": " 역할과 성과가 드러나지만 측정 기준을 보강해야 합니다. ",
                    "strengths": [" 역할을 구체적으로 설명했습니다. ", " 성과를 수치로 제시했습니다. "],
                    "improvements": [" 성과 측정 기준을 명확히 하세요. "]
                  },
                  "score": 82,
                  "followUpQuestion": " 성과를 측정한 기준은 무엇이었나요? "
                }
                """);
        InterviewMessageFeedbackClient client = new OpenAiInterviewMessageFeedbackClient(
                ChatClient.builder(chatModel)
        );

        InterviewMessageFeedbackResult result = client.generate(request());

        assertThat(result.content())
                .isEqualTo("역할과 성과를 연결해 설명한 점이 좋습니다. 이어서 성과를 측정한 기준을 설명해 주세요.");
        assertThat(result.feedbackSummary())
                .isEqualTo("역할과 성과가 드러나지만 측정 기준을 보강해야 합니다.");
        assertThat(result.feedbackStrengths())
                .containsExactly("역할을 구체적으로 설명했습니다.", "성과를 수치로 제시했습니다.");
        assertThat(result.feedbackImprovements())
                .containsExactly("성과 측정 기준을 명확히 하세요.");
        assertThat(result.score()).isEqualTo(82);
        assertThat(result.followUpQuestion()).isEqualTo("성과를 측정한 기준은 무엇이었나요?");

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText())
                .contains("한국어 채용 면접", "최신 USER 답변", "1~100", "꼬리질문", "JSON 객체");
        String userPrompt = prompt.getUserMessage().getText();
        assertThat(userPrompt).contains(
                "프로젝트에서 맡은 역할과 성과를 설명해 주세요.",
                "첫 번째 답변입니다.",
                "성과를 수치로 설명해 주세요.",
                "두 번째 답변입니다."
        );
        assertThat(userPrompt.indexOf("첫 번째 답변입니다."))
                .isLessThan(userPrompt.indexOf("성과를 수치로 설명해 주세요."));
        assertThat(userPrompt.indexOf("성과를 수치로 설명해 주세요."))
                .isLessThan(userPrompt.indexOf("두 번째 답변입니다."));
    }

    @Test
    void acceptsScoreBoundaryValues() {
        for (int score : List.of(1, 100)) {
            InterviewMessageFeedbackResult result = clientReturning(validResponse(score)).generate(request());

            assertThat(result.score()).isEqualTo(score);
        }
    }

    @Test
    void includesOriginalQuestionForFirstUserAnswerWithoutAssistantHistory() {
        CapturingChatModel chatModel = new CapturingChatModel(validResponse(80));
        InterviewMessageFeedbackClient client = new OpenAiInterviewMessageFeedbackClient(
                ChatClient.builder(chatModel)
        );
        InterviewMessageFeedbackRequest request = new InterviewMessageFeedbackRequest(
                "지원 동기를 설명해 주세요.",
                List.of(new InterviewMessageFeedbackMessage(
                        InterviewMessageRole.USER,
                        "서비스의 성장 가능성에 매력을 느꼈습니다."
                ))
        );

        client.generate(request);

        assertThat(chatModel.capturedPrompt().getUserMessage().getText())
                .contains(
                        "지원 동기를 설명해 주세요.",
                        "USER",
                        "서비스의 성장 가능성에 매력을 느꼈습니다."
                );
    }

    @Test
    void rejectsInvalidFeedbackResponses() {
        List<String> invalidResponses = List.of(
                "null",
                """
                        {"content":"응답","feedback":null,"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":" ","feedback":{"summary":"요약","strengths":["강점"],"improvements":["개선"]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":" ","strengths":["강점"],"improvements":["개선"]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":null,"improvements":["개선"]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":[],"improvements":["개선"]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":[" "],"improvements":["개선"]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":["강점"],"improvements":[]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":["강점"],"improvements":[null]},"score":80,"followUpQuestion":"질문?"}
                        """,
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":["강점"],"improvements":["개선"]},"score":null,"followUpQuestion":"질문?"}
                        """,
                validResponse(0),
                validResponse(101),
                """
                        {"content":"응답","feedback":{"summary":"요약","strengths":["강점"],"improvements":["개선"]},"score":80,"followUpQuestion":" "}
                        """
        );

        for (String response : invalidResponses) {
            assertOutputValidationFailure(() -> clientReturning(response).generate(request()));
        }
    }

    @Test
    void classifiesMalformedJsonAsOutputValidationFailure() {
        assertOutputValidationFailure(() -> clientReturning("not-json").generate(request()));
    }

    @Test
    void wrapsProviderFailureAndPreservesCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        ChatModel failingModel = prompt -> {
            throw cause;
        };
        InterviewMessageFeedbackClient client = new OpenAiInterviewMessageFeedbackClient(
                ChatClient.builder(failingModel)
        );

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(InterviewMessageFeedbackClientException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(InterviewMessageFeedbackClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private InterviewMessageFeedbackClient clientReturning(String response) {
        return new OpenAiInterviewMessageFeedbackClient(
                ChatClient.builder(new CapturingChatModel(response))
        );
    }

    private void assertOutputValidationFailure(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(InterviewMessageFeedbackClientException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(InterviewMessageFeedbackClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    private InterviewMessageFeedbackRequest request() {
        return new InterviewMessageFeedbackRequest(
                "프로젝트에서 맡은 역할과 성과를 설명해 주세요.",
                List.of(
                        new InterviewMessageFeedbackMessage(InterviewMessageRole.USER, "첫 번째 답변입니다."),
                        new InterviewMessageFeedbackMessage(
                                InterviewMessageRole.ASSISTANT,
                                "성과를 수치로 설명해 주세요."
                        ),
                        new InterviewMessageFeedbackMessage(InterviewMessageRole.USER, "두 번째 답변입니다.")
                )
        );
    }

    private String validResponse(int score) {
        return """
                {
                  "content": "응답",
                  "feedback": {
                    "summary": "요약",
                    "strengths": ["강점"],
                    "improvements": ["개선"]
                  },
                  "score": %d,
                  "followUpQuestion": "질문?"
                }
                """.formatted(score);
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
