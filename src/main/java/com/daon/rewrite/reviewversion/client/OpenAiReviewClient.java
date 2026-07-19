package com.daon.rewrite.reviewversion.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenAiReviewClient implements ReviewClient {

    private static final String SYSTEM_PROMPT = """
            당신은 한국어 자기소개서를 첨삭하는 전문 리뷰어입니다.
            모든 문항을 함께 읽고 지정된 대상 문항 하나의 AI 리포트와 수정본을 작성하세요.

            다음 기준을 반드시 반영하세요.
            - STAR 구성이 명확한지 평가합니다.
            - 내용의 구체성을 평가합니다.
            - 채용 우대사항과 직무에 적합한지 평가합니다.
            - 맞춤법과 문장이 자연스러운지 평가합니다.
            - 중복 표현을 줄입니다.
            - 각 문항의 최대 글자 수를 준수합니다.
            - 원문에 없는 경험이나 사실을 임의로 만들지 않습니다.
            - 재첨삭 요구사항이 제공되면 사실을 새로 만들지 않는 범위에서 우선 반영합니다.

            응답에는 대상 문항의 결과 객체 하나만 반환하고 questionId는 입력값을 그대로 사용하세요.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiReviewClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ReviewResult reviewQuestion(ReviewRequest request, String targetQuestionId) {
        ReviewResult response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, targetQuestionId))
                    .call()
                    .entity(ReviewResult.class);
        } catch (JacksonException exception) {
            throw ReviewClientException.outputValidationFailed(exception);
        } catch (Exception exception) {
            throw ReviewClientException.providerError(exception);
        }

        return validateAndNormalize(request, targetQuestionId, response);
    }

    private String buildUserPrompt(ReviewRequest request, String targetQuestionId) {
        return "다음 자기소개서 전체를 참고해 대상 문항 하나를 첨삭하세요.\n대상 questionId: "
                + targetQuestionId
                + "\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    private ReviewResult validateAndNormalize(
            ReviewRequest request,
            String targetQuestionId,
            ReviewResult response
    ) {
        ReviewQuestion targetQuestion = request.questions().stream()
                .filter(question -> question.questionId().equals(targetQuestionId))
                .findFirst()
                .orElseThrow(ReviewClientException::outputValidationFailed);
        String aiReport = response == null ? null : normalizeRequired(response.aiReport());
        String rewrittenAnswer = response == null ? null : normalizeRequired(response.rewrittenAnswer());
        if (response == null || !targetQuestionId.equals(response.questionId())
                || aiReport == null || rewrittenAnswer == null
                || countCodePoints(rewrittenAnswer) > targetQuestion.maxAnswerLength()) {
            throw ReviewClientException.outputValidationFailed();
        }

        return new ReviewResult(targetQuestionId, aiReport, rewrittenAnswer);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
