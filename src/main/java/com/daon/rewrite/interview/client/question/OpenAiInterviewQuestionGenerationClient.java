package com.daon.rewrite.interview.client.question;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 전체 자기소개서 문맥을 OpenAI에 전달하고 요청 개수·빈 질문·문자열 중복을 검증한다.
 * 자기소개서와의 관련성이나 질문의 의미상 중복은 프롬프트로 요청하며 코드에서 별도로 판정하지 않는다.
 */
@Component
public class OpenAiInterviewQuestionGenerationClient implements InterviewQuestionGenerationClient {

    private static final String SYSTEM_PROMPT = """
            당신은 한국어 채용 면접 질문을 설계하는 전문가입니다.
            지원 회사, 지원 직무, 채용 우대사항과 자기소개서 최종 작성본을 읽고 자기소개서 기반 예상 면접 질문을 생성하세요.

            다음 기준을 반드시 지키세요.
            - 입력 JSON의 questionCount로 요청된 개수만큼 정확히 반환합니다.
            - 모든 질문은 자기소개서 최종 작성본에 드러난 경험과 서술을 근거로 작성합니다.
            - 지원자의 역할, 행동, 성과, 문제 해결 과정, 의사결정을 구체적으로 확인합니다.
            - 자기소개서와 무관한 일반 기술 지식만 묻는 질문은 작성하지 않습니다.
            - existingQuestions 및 새로 생성하는 질문끼리 중복되는 질문을 반환하지 않습니다.
            - 질문은 지원자가 실제 면접에서 답변할 수 있는 완결된 문장으로 작성합니다.
            - 각 질문 항목은 question 필드만 포함합니다.
            - 응답은 JSON 객체 하나만 반환합니다.
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatClient chatClient;

    public OpenAiInterviewQuestionGenerationClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 프롬프트 구성·호출·응답 변환 중 발생한 JacksonException은 출력 검증 실패로 분류한다.
     * 그 밖의 예외는 provider 실패로 분류하고 객체 변환 후에는 질문 목록의 내용을 검증한다.
     */
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

        return validateAndNormalize(response, request);
    }

    private String buildUserPrompt(InterviewQuestionGenerationRequest request) {
        return "다음 지원 정보와 자기소개서 최종 작성본을 바탕으로 요청된 개수의 면접 질문을 생성하세요.\n입력 JSON:\n"
                + JSON_MAPPER.writeValueAsString(request);
    }

    /**
     * 서비스가 전달한 양수 questionCount와 응답 개수가 정확히 일치해야 한다.
     * 기존 질문과 생성 질문 전체를 검사하며 한 항목이라도 잘못되면 전체 생성 결과를 반환하지 않는다.
     */
    private List<InterviewQuestionGenerationResult> validateAndNormalize(
            OpenAiInterviewQuestionGenerationResponse response,
            InterviewQuestionGenerationRequest request
    ) {
        if (response == null || response.questions() == null
                || request.questionCount() <= 0
                || response.questions().size() != request.questionCount()) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }

        Set<String> seenQuestions = new HashSet<>();
        for (String existingQuestion : request.existingQuestions()) {
            String normalized = normalizeRequired(existingQuestion);
            if (normalized == null) {
                throw InterviewQuestionGenerationClientException.outputValidationFailed();
            }
            seenQuestions.add(normalized);
        }
        return response.questions().stream()
                .map(question -> normalize(question, seenQuestions))
                .toList();
    }

    /**
     * 앞뒤 공백 제거 후 정확히 같은 문자열이면 기존 질문·이번 생성 질문 사이 모두 중복으로 처리한다.
     * seenQuestions에는 검증을 통과한 새 질문도 추가해 응답 내부 중복까지 확인한다.
     * 질문 본문에는 별도의 문자 수 상한을 적용하지 않는다.
     */
    private InterviewQuestionGenerationResult normalize(
            OpenAiInterviewQuestionGenerationResponse.Question result,
            Set<String> seenQuestions
    ) {
        if (result == null) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }
        String question = normalizeRequired(result.question());
        if (question == null || !seenQuestions.add(question)) {
            throw InterviewQuestionGenerationClientException.outputValidationFailed();
        }
        return new InterviewQuestionGenerationResult(question);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
