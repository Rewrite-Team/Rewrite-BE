package com.daon.rewrite.reviewversion.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiFirstReviewClient implements FirstReviewClient {

    private static final String SYSTEM_PROMPT = """
            당신은 한국어 자기소개서를 첨삭하는 전문 리뷰어입니다.
            모든 문항을 함께 읽고 각 문항별 AI 리포트와 수정본을 작성하세요.

            다음 기준을 반드시 반영하세요.
            - STAR 구성이 명확한지 평가합니다.
            - 내용의 구체성을 평가합니다.
            - 채용 우대사항과 직무에 적합한지 평가합니다.
            - 맞춤법과 문장이 자연스러운지 평가합니다.
            - 중복 표현을 줄입니다.
            - 각 문항의 최대 글자 수를 준수합니다.
            - 원문에 없는 경험이나 사실을 임의로 만들지 않습니다.
            - 재첨삭 요구사항이 제공되면 사실을 새로 만들지 않는 범위에서 우선 반영합니다.

            응답의 questionId는 입력값을 그대로 사용하고 모든 문항의 결과를 정확히 한 번씩 반환하세요.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiFirstReviewClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<FirstReviewResult> review(FirstReviewRequest request) {
        OpenAiFirstReviewResponse response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request))
                    .call()
                    .entity(OpenAiFirstReviewResponse.class);
        } catch (JacksonException exception) {
            throw FirstReviewClientException.outputValidationFailed(exception);
        } catch (Exception exception) {
            throw FirstReviewClientException.providerError(exception);
        }

        return validateAndNormalize(request, response);
    }

    private String buildUserPrompt(FirstReviewRequest request) {
        return "다음 자기소개서 전체를 첨삭하세요.\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    private List<FirstReviewResult> validateAndNormalize(
            FirstReviewRequest request,
            OpenAiFirstReviewResponse response
    ) {
        if (response == null || response.results() == null
                || response.results().size() != request.questions().size()) {
            throw FirstReviewClientException.outputValidationFailed();
        }

        Map<String, FirstReviewQuestion> questionById = new HashMap<>();
        for (FirstReviewQuestion question : request.questions()) {
            questionById.put(question.questionId(), question);
        }

        Map<String, FirstReviewResult> normalizedResultById = new HashMap<>();
        for (FirstReviewResult result : response.results()) {
            if (result == null || result.questionId() == null || result.questionId().isBlank()
                    || normalizedResultById.containsKey(result.questionId())) {
                throw FirstReviewClientException.outputValidationFailed();
            }

            FirstReviewQuestion question = questionById.get(result.questionId());
            String aiReport = normalizeRequired(result.aiReport());
            String rewrittenAnswer = normalizeRequired(result.rewrittenAnswer());
            if (question == null || aiReport == null || rewrittenAnswer == null
                    || countCodePoints(rewrittenAnswer) > question.maxAnswerLength()) {
                throw FirstReviewClientException.outputValidationFailed();
            }

            normalizedResultById.put(result.questionId(), new FirstReviewResult(
                    result.questionId(),
                    aiReport,
                    rewrittenAnswer
            ));
        }

        if (!normalizedResultById.keySet().equals(questionById.keySet())) {
            throw FirstReviewClientException.outputValidationFailed();
        }

        return request.questions().stream()
                .map(question -> normalizedResultById.get(question.questionId()))
                .toList();
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
