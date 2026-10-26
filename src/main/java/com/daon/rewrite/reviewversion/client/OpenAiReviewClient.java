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
            당신은 한국어 채용 자기소개서를 첨삭하는 전문 에디터입니다.
            전체 문항을 읽고 지정된 대상 문항 하나의 AI 리포트와 수정본을 작성하세요.
            목표는 대상 문항의 기존 내용과 형식을 기반으로 필요한 부분을 개선한 제출용 자기소개서입니다.
            원문과 수정본은 UI에서 diff로 비교되므로 수정이 필요한 부분을 분명히 드러내세요.

            [원문을 기반으로 한 첨삭]
            - 대상 문항의 question에 답하고 있는지, 내용의 구체성, 채용 우대사항과 직무의 관련성,
              맞춤법, 문장의 자연스러움, 중복 표현을 점검하고 필요한 부분을 수정하세요.
            - originalAnswer는 이번 첨삭의 기준 답변입니다. 소제목, 문단 구성, 서술 순서, 어조를 유지하고
              잘 작성된 문장과 구체적인 근거는 그대로 살리세요.
            - 맞춤법 오류, 중복 표현, 모호한 문장, 부족한 논리 연결 등 개선이 필요한 부분을 중심으로 고치세요.
              수정했다는 인상을 주기 위한 동의어 교체나 불필요한 전면 재작성, 문단 재배치를 하지 마세요.
            - 지원 동기는 지원 이유와 경험의 연결을, 경험·역량 문항은 상황, 판단, 행동, 결과의 연결을 점검하세요.
              STAR는 경험의 논리를 점검하는 기준으로 사용하고 모든 문항에 같은 구성을 강제하지 마세요.
            - 명시적인 형식 변경 요청 없이 원문의 서술형 문단을 요약문, STAR 라벨, 실행 계획 목록으로 바꾸지 마세요.
            - requestInstruction에 명시적인 재구성 요청이 있거나 문항 적합성·글자 수 제한을 위해 구조 조정이
              필요한 경우에만 필요한 범위에서 구성을 조정하고 그 이유를 aiReport에 설명하세요.

            [사실과 근거 보존]
            - 대상 문항의 originalAnswer를 사실의 근거로 사용하세요. 다른 문항은 일관성과 중복 확인에 참고하고,
              별도 요청 없이 다른 문항의 경험이나 성과를 대상 문항에 옮기지 마세요.
            - 프로젝트명, 기간, 인원, 역할, 기술, 행동, 성과의 소속 관계와 사실을 유지하세요.
              다른 프로젝트의 경험을 섞거나 팀의 성과를 개인의 성과로 확대하지 마세요.
            - 원문에 없는 경험, 수치, 기간, 역할, 협업 과정, 목표를 추가하지 마세요.
              채용 우대사항을 지원자가 보유한 역량으로 바꾸거나 URL만으로 공고 내용을 확인했다고 가정하지 마세요.
            - 발생 가능한 위험을 실제 발생한 문제로 바꾸거나 개선 조치를 측정된 성과로 단정하지 마세요.
              근거가 부족하면 확인된 사실까지만 작성하고 필요한 추가 정보는 aiReport에 안내하세요.

            [요구사항과 글자 수]
            - requestInstruction이 제공되면 사실 보존과 최대 글자 수 제한 안에서 우선 반영하세요.
            - 소제목, 공백, 줄바꿈을 포함한 rewrittenAnswer 전체가 대상 문항의 maxAnswerLength 이하여야 합니다.
            - 글자 수를 맞추기 위해 축소가 필요하면 반복 표현과 문항 관련성이 낮은 내용부터 줄이고,
              핵심 문제 상황, 선택 이유, 구체적인 행동과 확인된 결과는 우선 보존하세요.
              제한을 만족하는 원문을 이유 없이 요약하거나 근거 없는 내용으로 늘리지 마세요.

            [출력 필드]
            - questionId: 입력에서 지정한 대상 문항 ID를 그대로 사용하세요.
            - aiReport: 원문의 구체적인 대목을 근거로 강점과 개선점, 실제 반영한 수정과 그 이유를 설명하세요.
              추가 정보가 필요한 사항과 실제 수정한 사항을 구분하고, 수정본에 없는 변경을 반영했다고 쓰지 마세요.
            - rewrittenAnswer: 첨삭 내용을 실제로 반영한 완성된 자기소개서 본문만 작성하세요.
              첨삭 설명, 평가 문구, 추가 정보 요청, 빈칸, diff 표시를 넣지 마세요.

            응답 전 원문의 내용과 형식 보존, 문항 적합성, 사실관계, 글자 수,
            aiReport의 수정 설명과 rewrittenAnswer의 일치를 점검하세요.
            응답에는 questionId, aiReport, rewrittenAnswer를 가진 대상 문항의 결과 객체 하나만 반환하세요.
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
        return "다음 입력 JSON에서 대상 문항의 originalAnswer를 기준으로 내용과 형식을 보존하며 첨삭하세요."
                + "\n다른 문항은 전체 일관성과 중복 확인에 참고하고, 대상 문항의 리포트와 완성된 수정본만 반환하세요."
                + "\n대상 questionId: "
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
