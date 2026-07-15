package com.daon.rewrite.interview.client;

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

class OpenAiInterviewQuestionGenerationClientTest {

    @Test
    void generatesAndNormalizesFiveCoverLetterBasedQuestionsWithoutTypes() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "questions": [
                    {"question":" 프로젝트에서 맡은 역할을 설명해 주세요. "},
                    {"question":"갈등을 해결한 과정을 설명해 주세요."},
                    {"question":"성과를 만들기 위해 어떤 행동을 했는지 설명해 주세요."},
                    {"question":"문제 해결 과정에서 내린 의사결정을 설명해 주세요."},
                    {"question":"지원 동기를 경험과 연결해 설명해 주세요."}
                  ]
                }
                """);
        InterviewQuestionGenerationClient client = new OpenAiInterviewQuestionGenerationClient(
                ChatClient.builder(chatModel)
        );

        List<InterviewQuestionGenerationResult> results = client.generate(request());

        assertThat(results)
                .extracting(InterviewQuestionGenerationResult::question)
                .containsExactly(
                        "프로젝트에서 맡은 역할을 설명해 주세요.",
                        "갈등을 해결한 과정을 설명해 주세요.",
                        "성과를 만들기 위해 어떤 행동을 했는지 설명해 주세요.",
                        "문제 해결 과정에서 내린 의사결정을 설명해 주세요.",
                        "지원 동기를 경험과 연결해 설명해 주세요."
                );

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText())
                .contains("면접 질문", "요청된 개수", "자기소개서", "경험", "성과", "의사결정")
                .doesNotContain("COVER_LETTER_BASED", "TECHNICAL", "type");
        assertThat(prompt.getUserMessage().getText())
                .contains(
                        "\"questionCount\":5",
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
    void generatesOneAdditionalQuestionAndRejectsExistingQuestion() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {"questions":[{"question":" 새로운 의사결정 질문 "}]}
                """);
        InterviewQuestionGenerationClient client = new OpenAiInterviewQuestionGenerationClient(
                ChatClient.builder(chatModel)
        );

        List<InterviewQuestionGenerationResult> results = client.generate(additionalRequest());

        assertThat(results)
                .extracting(InterviewQuestionGenerationResult::question)
                .containsExactly("새로운 의사결정 질문");
        assertThat(chatModel.capturedPrompt().getUserMessage().getText())
                .contains("\"questionCount\":1", "기존 역할 질문", "기존 성과 질문");

        InterviewQuestionGenerationClient duplicateClient = clientReturning("""
                {"questions":[{"question":" 기존 역할 질문 "}]}
                """);

        assertOutputValidationFailure(() -> duplicateClient.generate(additionalRequest()));
    }

    @Test
    void rejectsInvalidQuestionResponses() {
        List<String> invalidResponses = List.of(
                """
                        {"questions":null}
                        """,
                """
                        {"questions":[
                          {"question":"질문 1"},
                          {"question":"질문 2"},
                          {"question":"질문 3"},
                          {"question":"질문 4"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"question":"질문 1"},
                          {"question":"질문 2"},
                          {"question":" "},
                          {"question":"질문 4"},
                          {"question":"질문 5"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"question":"중복 질문"},
                          {"question":"질문 2"},
                          {"question":"질문 3"},
                          {"question":" 중복 질문 "},
                          {"question":"질문 5"}
                        ]}
                        """,
                """
                        {"questions":["not-object"]}
                        """
        );

        for (String response : invalidResponses) {
            InterviewQuestionGenerationClient client = clientReturning(response);

            assertOutputValidationFailure(() -> client.generate(request()));
        }
    }

    @Test
    void classifiesMalformedJsonAsOutputValidationFailure() {
        InterviewQuestionGenerationClient client = clientReturning("not-json");

        assertOutputValidationFailure(() -> client.generate(request()));
    }

    @Test
    void wrapsProviderFailureAndPreservesCause() {
        IllegalStateException cause = new IllegalStateException("provider unavailable");
        ChatModel failingModel = prompt -> {
            throw cause;
        };
        InterviewQuestionGenerationClient client = new OpenAiInterviewQuestionGenerationClient(
                ChatClient.builder(failingModel)
        );

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(InterviewQuestionGenerationClientException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(InterviewQuestionGenerationClientException.Reason.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private InterviewQuestionGenerationClient clientReturning(String response) {
        return new OpenAiInterviewQuestionGenerationClient(
                ChatClient.builder(new CapturingChatModel(response))
        );
    }

    private void assertOutputValidationFailure(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(InterviewQuestionGenerationClientException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(InterviewQuestionGenerationClientException.Reason.OUTPUT_VALIDATION_FAILED));
    }

    private InterviewQuestionGenerationRequest request() {
        return new InterviewQuestionGenerationRequest(
                "다온",
                "백엔드 개발자",
                "Spring 경험 우대",
                5,
                List.of(),
                List.of(
                        new InterviewQuestionGenerationAnswer("clq_1", 1, "지원 동기는?", "최종 작성본 1"),
                        new InterviewQuestionGenerationAnswer("clq_2", 2, "직무 역량은?", "최종 작성본 2")
                )
        );
    }

    private InterviewQuestionGenerationRequest additionalRequest() {
        return new InterviewQuestionGenerationRequest(
                "다온",
                "백엔드 개발자",
                "Spring 경험 우대",
                1,
                List.of("기존 역할 질문", "기존 성과 질문"),
                List.of(
                        new InterviewQuestionGenerationAnswer("clq_1", 1, "지원 동기는?", "최종 작성본 1"),
                        new InterviewQuestionGenerationAnswer("clq_2", 2, "직무 역량은?", "최종 작성본 2")
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
