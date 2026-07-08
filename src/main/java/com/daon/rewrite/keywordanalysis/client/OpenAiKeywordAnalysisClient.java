package com.daon.rewrite.keywordanalysis.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class OpenAiKeywordAnalysisClient implements KeywordAnalysisClient {

    private static final int MAX_KEYWORD_COUNT = 20;
    private static final int MIN_IMPORTANCE = 1;
    private static final int MAX_IMPORTANCE = 100;
    private static final String SYSTEM_PROMPT = """
            당신은 한국어 자기소개서의 핵심 키워드를 분석하는 전문가입니다.
            자기소개서의 문항과 최종 작성본을 함께 읽고 워드클라우드에 사용할 핵심 키워드를 추출하세요.

            다음 기준을 반드시 지키세요.
            - 핵심 키워드는 중요도 기준 상위 20개를 반환합니다.
            - importance는 1~100 범위의 정수입니다.
            - keyword는 짧은 명사 또는 명사구로 작성합니다.
            - 같은 의미의 키워드를 중복 반환하지 않습니다.
            - 응답은 JSON 객체 하나만 반환합니다.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiKeywordAnalysisClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<KeywordAnalysisResult> analyze(KeywordAnalysisRequest request) {
        OpenAiKeywordAnalysisResponse response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request))
                    .call()
                    .entity(OpenAiKeywordAnalysisResponse.class);
        } catch (JacksonException exception) {
            throw KeywordAnalysisClientException.outputValidationFailed(exception);
        } catch (Exception exception) {
            throw KeywordAnalysisClientException.providerError(exception);
        }

        return validateAndNormalize(response);
    }

    private String buildUserPrompt(KeywordAnalysisRequest request) {
        return "다음 자기소개서 최종 작성본의 핵심 키워드를 분석하세요.\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    private List<KeywordAnalysisResult> validateAndNormalize(OpenAiKeywordAnalysisResponse response) {
        if (response == null || response.keywords() == null
                || response.keywords().isEmpty()) {
            throw KeywordAnalysisClientException.outputValidationFailed();
        }

        Set<String> seenKeywords = new HashSet<>();
        List<KeywordAnalysisResult> normalizedResults = response.keywords().stream()
                .map(result -> normalize(result, seenKeywords))
                .toList();

        return normalizedResults.stream()
                .sorted(Comparator.comparingInt(KeywordAnalysisResult::importance).reversed())
                .limit(MAX_KEYWORD_COUNT)
                .toList();
    }

    private KeywordAnalysisResult normalize(KeywordAnalysisResult result, Set<String> seenKeywords) {
        if (result == null) {
            throw KeywordAnalysisClientException.outputValidationFailed();
        }
        String keyword = normalizeRequired(result.keyword());
        if (keyword == null
                || result.importance() < MIN_IMPORTANCE
                || result.importance() > MAX_IMPORTANCE
                || !seenKeywords.add(keyword)) {
            throw KeywordAnalysisClientException.outputValidationFailed();
        }
        return new KeywordAnalysisResult(keyword, result.importance());
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
