package com.daon.rewrite.reviewversion.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 전체 자기소개서와 대상 문항을 OpenAI에 전달하고 문항별 첨삭 결과로 변환한다.
 * 대상 ID·필수 문자열·최대 글자 수를 검증한 결과만 반환하며 저장이나 재시도는 Job 계층에 맡긴다.
 */
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

    /**
     * 응답의 JSON 변환 실패는 출력 검증 실패로, 호출 중 그 밖의 예외는 provider 실패로 구분한다.
     * 변환 뒤에도 대상 문항 일치와 문자열 내용을 검증해 형식만 맞는 잘못된 결과를 거부한다.
     */
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

    /**
     * 리포트와 수정본은 앞뒤 공백 제거 후 비어 있지 않아야 하고, 수정본 길이는 대상 문항 제한을 따른다.
     * 글자 수는 Java UTF-16 길이 대신 Unicode code point 기준으로 비교한다.
     */
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
