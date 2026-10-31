package com.daon.rewrite.llmjob.controller;

import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import com.daon.rewrite.llmjob.dto.LlmJobStateResponse;
import com.daon.rewrite.llmjob.service.LlmJobAccessService;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 도메인별 시작 API가 반환한 Job을 상태 조회와 SSE로 추적하는 공통 HTTP 진입점.
 * 조회는 LlmJobAccessService에, 연결 등록·초기 상태 전송·후속 이벤트는 LlmJobStreamService에 맡긴다.
 */
@RestController
@RequiredArgsConstructor
public class LlmJobController {

    private final LlmJobAccessService llmJobAccessService;
    private final LlmJobStreamService llmJobStreamService;

    @GetMapping("/llm-jobs/{jobId}")
    @RewriteApi(
            id = "API-015",
            operationId = "getLlmJobStatus",
            summary = "Job 상태 조회",
            tag = "LLMJobs",
            purpose = "비동기 LLM Job의 현재 상태를 조회해 SSE 실패 시 최종 상태를 복구한다.",
            screens = {"첨삭 진행", "키워드 분석", "AI 면접"},
            trigger = "API-016 연결·재연결 실패, 이벤트 유실 또는 새로고침 시 polling fallback으로 호출한다.",
            behavior = "PENDING·PROCESSING 진행률과 COMPLETED·FAILED·CANCELED 종료 상태를 반환한다. status=FAILED는 HTTP 오류가 아닌 200 응답이다.",
            success = "종료 상태에서 polling을 멈추고 resultRef에 맞는 도메인 조회 또는 error.code 기반 실패 처리를 수행한다.",
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "Job 없음, 비소유 또는 삭제된 자기소개서에 연결된 Job",
                    action = "polling을 종료하고 원래 도메인 화면 또는 목록을 다시 조회한다."
            )
    )
    public LlmJobStateResponse findJob(@PathVariable String jobId) {
        return LlmJobStateResponse.from(llmJobAccessService.findMyJob(jobId));
    }

    @GetMapping(path = "/llm-jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(
            responseCode = "200",
            description = "job.state와 도메인별 중간 결과 SSE 스트림",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string"),
                    examples = {
                            @ExampleObject(
                                    name = "coverLetterReview",
                                    summary = "최초 첨삭 상태와 완료 문항",
                                    value = LlmJobStreamDocumentation.COVER_LETTER_REVIEW
                            ),
                            @ExampleObject(
                                    name = "coverLetterReReview",
                                    summary = "재첨삭 상태와 완료 문항",
                                    value = LlmJobStreamDocumentation.COVER_LETTER_RE_REVIEW
                            ),
                            @ExampleObject(
                                    name = "keywordAnalysis",
                                    summary = "키워드 분석 상태와 결과 참조",
                                    value = LlmJobStreamDocumentation.KEYWORD_ANALYSIS
                            ),
                            @ExampleObject(
                                    name = "interviewInitialQuestionGeneration",
                                    summary = "초기 면접 질문 생성 상태와 세션 참조",
                                    value = LlmJobStreamDocumentation.INTERVIEW_INITIAL_QUESTION_GENERATION
                            ),
                            @ExampleObject(
                                    name = "interviewAdditionalQuestionGeneration",
                                    summary = "추가 면접 질문 생성 상태와 질문 참조",
                                    value = LlmJobStreamDocumentation.INTERVIEW_ADDITIONAL_QUESTION_GENERATION
                            ),
                            @ExampleObject(
                                    name = "interviewMessageFeedback",
                                    summary = "면접 답변 피드백 상태와 조각",
                                    description = "전체 AI 응답을 검증한 뒤 완성된 content를 분할 전송하며, sequence 순서로 contentDelta를 이어 붙인다.",
                                    value = LlmJobStreamDocumentation.INTERVIEW_MESSAGE_FEEDBACK
                            )
                    }
            )
    )
    @RewriteApi(
            id = "API-016",
            operationId = "streamLlmJobEvents",
            summary = "Job 스트림",
            tag = "LLMJobs",
            purpose = "첨삭·키워드 분석·AI 면접 Job의 상태와 도메인별 중간 결과를 하나의 SSE 연결로 수신한다.",
            screens = {"첨삭 진행", "키워드 분석", "AI 면접"},
            trigger = "Job 시작 API가 반환한 jobId로 진행 상태를 구독할 때 연결한다.",
            behavior = LlmJobStreamDocumentation.BEHAVIOR,
            success = """
                    서버가 최종 job.state와 도메인별 후속 이벤트 전송을 마친 뒤 연결을 종료한다. 클라이언트는 terminal 상태에 따라 API별 결과 조회 또는 실패 처리를 수행한다.
                    FAILED이면 error.code에 따라 실패를 표시하고 면접 피드백의 임시 문장은 제거한다. CANCELED이면 부분 결과를 폐기한 뒤 원래 화면·목록을 재조회한다.
                    재연결 시 최신 상태와 첨삭 완료 문항 스냅샷을 받는다. 면접 피드백은 PROCESSING 동안 서버 메모리에 남은 조각을 sequence=1부터 다시 보내므로 이미 반영한 sequence는 무시한다. 버퍼가 없으면 진행 상태만 유지하고 완료 후 API-029로 전체 결과를 조회한다.
                    Last-Event-ID 기반 영속 replay는 제공하지 않는다.
                    addEventListener(\"heartbeat\", ...)로 수신하며 heartbeat·업무 이벤트마다 무응답 감시 기준을 갱신한다.
                    기본 30초 무응답이면 연결을 닫고 API-015로 상태를 확인한 뒤 진행 중인 Job만 재연결한다. 전송 주기를 바꾸면 무응답 기준도 더 긴 값으로 조정한다.
                    onerror에서도 연결을 닫고 API-015로 인증·대상·현재 상태를 확인한다. 장애가 반복되면 재연결 간격을 늘리고 API-015 또는 도메인 조회 polling으로 전환하며 NOT_FOUND이면 복구를 중단한다.
                    """,
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "Job 없음, 접근 불가 또는 삭제된 자기소개서에 연결된 Job",
                    action = "스트림 재연결을 중단하고 API-015 또는 도메인 조회로 최종 상태를 확인한다."
            )
    )
    public SseEmitter streamJob(@PathVariable String jobId) {
        return llmJobStreamService.openMyJobStream(jobId);
    }
}
