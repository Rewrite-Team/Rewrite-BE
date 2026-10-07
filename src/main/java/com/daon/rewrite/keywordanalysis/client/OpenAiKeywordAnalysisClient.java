package com.daon.rewrite.keywordanalysis.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 지원 정보와 문항별 최종 작성본을 OpenAI에 전달하고 워드클라우드용 키워드로 변환한다.
 * 전체 응답의 내용을 검증한 뒤 중요도 순으로 결과를 반환하며 영속 저장은 Job 계층에 맡긴다.
 */
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

    /**
     * 프롬프트 구성·호출·응답 변환 중 발생한 JacksonException은 출력 검증 실패로 분류한다.
     * 그 밖의 예외는 provider 실패로 분류하고 객체 변환 후에는 목록과 각 키워드의 내용을 검증한다.
     */
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

    /**
     * 빈 응답이나 항목 하나의 검증 실패도 전체 분석 실패로 처리한다.
     * 모든 항목을 검증한 뒤 중요도 내림차순의 최대 20개를 반환하며 20개 미만도 허용한다.
     */
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

    /**
     * 키워드는 앞뒤 공백 제거 후 비어 있지 않아야 하고 중요도는 1~100이어야 한다.
     * 중복 검사는 앞뒤 공백 제거 후 정확히 같은 문자열에만 적용하며 의미가 같은 다른 단어까지 비교하지 않는다.
     */
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
