package com.daon.rewrite.interview.client;

import com.daon.rewrite.interview.entity.InterviewQuestionType;
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
    void generatesAndNormalizesThreeCoverLetterAndTwoTechnicalQuestions() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "questions": [
                    {"type":"TECHNICAL","question":" REST API의 멱등성을 설명해 주세요. "},
                    {"type":"COVER_LETTER_BASED","question":"프로젝트에서 맡은 역할을 설명해 주세요."},
                    {"type":"COVER_LETTER_BASED","question":"갈등을 해결한 과정을 설명해 주세요."},
                    {"type":"TECHNICAL","question":"트랜잭션 격리 수준을 설명해 주세요."},
                    {"type":"COVER_LETTER_BASED","question":"지원 동기를 경험과 연결해 설명해 주세요."}
                  ]
                }
                """);
        InterviewQuestionGenerationClient client = new OpenAiInterviewQuestionGenerationClient(
                ChatClient.builder(chatModel)
        );

        List<InterviewQuestionGenerationResult> results = client.generate(request());

        assertThat(results)
                .extracting(InterviewQuestionGenerationResult::type, InterviewQuestionGenerationResult::question)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                InterviewQuestionType.COVER_LETTER_BASED,
                                "프로젝트에서 맡은 역할을 설명해 주세요."
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                InterviewQuestionType.COVER_LETTER_BASED,
                                "갈등을 해결한 과정을 설명해 주세요."
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                InterviewQuestionType.COVER_LETTER_BASED,
                                "지원 동기를 경험과 연결해 설명해 주세요."
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                InterviewQuestionType.TECHNICAL,
                                "REST API의 멱등성을 설명해 주세요."
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                InterviewQuestionType.TECHNICAL,
                                "트랜잭션 격리 수준을 설명해 주세요."
                        )
                );

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText())
                .contains("면접 질문", "정확히 5개", "COVER_LETTER_BASED", "TECHNICAL", "3개", "2개");
        assertThat(prompt.getUserMessage().getText())
                .contains(
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
    void rejectsInvalidQuestionResponses() {
        List<String> invalidResponses = List.of(
                """
                        {"questions":null}
                        """,
                """
                        {"questions":[
                          {"type":"COVER_LETTER_BASED","question":"질문 1"},
                          {"type":"COVER_LETTER_BASED","question":"질문 2"},
                          {"type":"COVER_LETTER_BASED","question":"질문 3"},
                          {"type":"TECHNICAL","question":"질문 4"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"type":"COVER_LETTER_BASED","question":"질문 1"},
                          {"type":"COVER_LETTER_BASED","question":"질문 2"},
                          {"type":"TECHNICAL","question":"질문 3"},
                          {"type":"TECHNICAL","question":"질문 4"},
                          {"type":"TECHNICAL","question":"질문 5"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"type":"COVER_LETTER_BASED","question":"질문 1"},
                          {"type":"COVER_LETTER_BASED","question":"질문 2"},
                          {"type":"COVER_LETTER_BASED","question":"질문 3"},
                          {"type":"TECHNICAL","question":"질문 4"},
                          {"type":"UNKNOWN","question":"질문 5"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"type":"COVER_LETTER_BASED","question":"질문 1"},
                          {"type":"COVER_LETTER_BASED","question":"질문 2"},
                          {"type":"COVER_LETTER_BASED","question":" "},
                          {"type":"TECHNICAL","question":"질문 4"},
                          {"type":"TECHNICAL","question":"질문 5"}
                        ]}
                        """,
                """
                        {"questions":[
                          {"type":"COVER_LETTER_BASED","question":"중복 질문"},
                          {"type":"COVER_LETTER_BASED","question":"질문 2"},
                          {"type":"COVER_LETTER_BASED","question":"질문 3"},
                          {"type":"TECHNICAL","question":" 중복 질문 "},
                          {"type":"TECHNICAL","question":"질문 5"}
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
