package com.daon.rewrite.interview.client.feedback;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 원본 질문과 전체 대화 이력을 OpenAI에 전달하고 완성된 응답 전체를 검증한 뒤 반환한다.
 * 프롬프트는 첫 USER 답변을 원본 질문, 이후 답변을 이전 ASSISTANT의 꼬리질문 기준으로 평가하도록 요청한다.
 * 토큰 스트리밍은 사용하지 않으며 Job 계층이 검증된 content를 화면용 조각으로 전송한다.
 */
@Component
public class OpenAiInterviewMessageFeedbackClient implements InterviewMessageFeedbackClient {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 100;
    private static final String SYSTEM_PROMPT = """
            당신은 한국어 채용 면접 답변을 평가하고 다음 질문을 제시하는 면접 코치입니다.
            원본 면접 질문과 시간순 대화 이력을 읽고 최신 USER 답변을 평가하세요.

            다음 기준을 반드시 지키세요.
            - 첫 USER 답변은 원본 면접 질문을 기준으로 평가합니다.
            - 이전 ASSISTANT 메시지가 있으면 그 메시지의 꼬리질문과 이어지는 최신 USER 답변을 평가합니다.
            - content는 핵심 피드백과 다음 꼬리질문을 자연스럽게 연결한 하나의 메시지입니다.
            - feedback.summary는 답변의 전반적인 평가를 간결하게 작성합니다.
            - feedback.strengths와 feedback.improvements는 각각 하나 이상의 구체적인 항목을 반환합니다.
            - score는 답변 품질을 나타내는 1~100 범위의 정수입니다.
            - followUpQuestion은 최신 답변을 더 구체화하는 완결된 꼬리질문입니다.
            - 모든 결과는 한국어로 작성합니다.
            - 응답은 JSON 객체 하나만 반환합니다.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiInterviewMessageFeedbackClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 프롬프트 구성·호출·응답 변환 중 발생한 JacksonException은 출력 검증 실패로 분류한다.
     * 그 밖의 예외는 provider 실패로 분류하고 객체 변환 후에는 피드백 각 필드의 내용을 검증한다.
     */
    @Override
    public InterviewMessageFeedbackResult generate(InterviewMessageFeedbackRequest request) {
        OpenAiInterviewMessageFeedbackResponse response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request))
                    .call()
                    .entity(OpenAiInterviewMessageFeedbackResponse.class);
        } catch (JacksonException exception) {
            throw InterviewMessageFeedbackClientException.outputValidationFailed(exception);
        } catch (Exception exception) {
            throw InterviewMessageFeedbackClientException.providerError(exception);
        }

        return validateAndNormalize(response);
    }

    private String buildUserPrompt(InterviewMessageFeedbackRequest request) {
        return "다음 원본 면접 질문과 시간순 대화 이력을 바탕으로 최신 USER 답변을 평가하세요.\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    /**
     * 표시 문장·요약·꼬리질문과 강점·개선점 각 항목이 공백 제거 후 비어 있지 않고 점수가 1~100이어야 한다.
     * 모든 필드를 통과해야 결과를 반환하며 content와 구조화 필드의 의미상 일치까지 검사하지는 않는다.
     * 사용자 답변의 2000자 제한은 서비스에서 검증하며 이 출력에는 별도의 문자 수 상한을 적용하지 않는다.
     */
    private InterviewMessageFeedbackResult validateAndNormalize(
            OpenAiInterviewMessageFeedbackResponse response
    ) {
        if (response == null || response.feedback() == null || response.score() == null) {
            throw InterviewMessageFeedbackClientException.outputValidationFailed();
        }

        String content = normalizeRequired(response.content());
        String summary = normalizeRequired(response.feedback().summary());
        List<String> strengths = normalizeRequiredList(response.feedback().strengths());
        List<String> improvements = normalizeRequiredList(response.feedback().improvements());
        String followUpQuestion = normalizeRequired(response.followUpQuestion());
        if (content == null
                || summary == null
                || response.score() < MIN_SCORE
                || response.score() > MAX_SCORE
                || followUpQuestion == null) {
            throw InterviewMessageFeedbackClientException.outputValidationFailed();
        }

        return new InterviewMessageFeedbackResult(
                content,
                summary,
                strengths,
                improvements,
                response.score(),
                followUpQuestion
        );
    }

    /** 강점과 개선점은 각각 한 항목 이상 필요하며 빈 항목을 제거해 통과시키지 않는다. */
    private List<String> normalizeRequiredList(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw InterviewMessageFeedbackClientException.outputValidationFailed();
        }
        return values.stream()
                .map(this::normalizeRequiredItem)
                .toList();
    }

    private String normalizeRequiredItem(String value) {
        String normalized = normalizeRequired(value);
        if (normalized == null) {
            throw InterviewMessageFeedbackClientException.outputValidationFailed();
        }
        return normalized;
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
