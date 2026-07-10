package com.daon.rewrite.interview.client;

import com.daon.rewrite.interview.entity.InterviewQuestionType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class OpenAiInterviewQuestionGenerationClient implements InterviewQuestionGenerationClient {

    private static final int TOTAL_QUESTION_COUNT = 5;
    private static final int COVER_LETTER_BASED_COUNT = 3;
    private static final int TECHNICAL_COUNT = 2;
    private static final String SYSTEM_PROMPT = """
            당신은 한국어 채용 면접 질문을 설계하는 전문가입니다.
            지원 회사, 지원 직무, 채용 우대사항과 자기소개서 최종 작성본을 읽고 예상 면접 질문을 생성하세요.

            다음 기준을 반드시 지키세요.
            - 질문은 정확히 5개를 반환합니다.
            - COVER_LETTER_BASED 질문은 자기소개서 경험과 서술을 구체적으로 확인하는 질문 3개입니다.
            - TECHNICAL 질문은 지원 직무의 실무 역량을 확인하는 질문 2개입니다.
            - 서로 중복되는 질문을 반환하지 않습니다.
            - 질문은 지원자가 실제 면접에서 답변할 수 있는 완결된 문장으로 작성합니다.
            - type은 COVER_LETTER_BASED 또는 TECHNICAL 중 하나입니다.
            - 응답은 JSON 객체 하나만 반환합니다.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiInterviewQuestionGenerationClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<InterviewQuestionGenerationResult> generate(InterviewQuestionGenerationRequest request) {
        OpenAiInterviewQuestionGenerationResponse response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request))
                    .call()
                    .entity(OpenAiInterviewQuestionGenerationResponse.class);
        } catch (JacksonException exception) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed(exception);
        } catch (Exception exception) {
            throw InterviewQuestionGenerationClientException.providerError(exception);
        }

        return validateAndNormalize(response);
    }

    private String buildUserPrompt(InterviewQuestionGenerationRequest request) {
        return "다음 지원 정보와 자기소개서 최종 작성본을 바탕으로 초기 면접 질문을 생성하세요.\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    private List<InterviewQuestionGenerationResult> validateAndNormalize(
            OpenAiInterviewQuestionGenerationResponse response
    ) {
        if (response == null || response.questions() == null
                || response.questions().size() != TOTAL_QUESTION_COUNT) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }

        Set<String> seenQuestions = new HashSet<>();
        List<InterviewQuestionGenerationResult> coverLetterQuestions = new ArrayList<>();
        List<InterviewQuestionGenerationResult> technicalQuestions = new ArrayList<>();
        for (OpenAiInterviewQuestionGenerationResponse.Question question : response.questions()) {
            InterviewQuestionGenerationResult result = normalize(question, seenQuestions);
            if (result.type() == InterviewQuestionType.COVER_LETTER_BASED) {
                coverLetterQuestions.add(result);
            } else {
                technicalQuestions.add(result);
            }
        }

        if (coverLetterQuestions.size() != COVER_LETTER_BASED_COUNT
                || technicalQuestions.size() != TECHNICAL_COUNT) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }

        List<InterviewQuestionGenerationResult> normalizedResults = new ArrayList<>(TOTAL_QUESTION_COUNT);
        normalizedResults.addAll(coverLetterQuestions);
        normalizedResults.addAll(technicalQuestions);
        return List.copyOf(normalizedResults);
    }

    private InterviewQuestionGenerationResult normalize(
            OpenAiInterviewQuestionGenerationResponse.Question result,
            Set<String> seenQuestions
    ) {
        if (result == null) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }
        String type = normalizeRequired(result.type());
        String question = normalizeRequired(result.question());
        if (type == null || question == null || !seenQuestions.add(question)) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }

        try {
            return new InterviewQuestionGenerationResult(InterviewQuestionType.valueOf(type), question);
        } catch (IllegalArgumentException exception) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed(exception);
        }
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
