package com.daon.rewrite.llmjob.controller;

import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import com.daon.rewrite.llmjob.dto.LlmJobStateResponse;
import com.daon.rewrite.llmjob.service.LlmJobService;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class LlmJobController {

    private final LlmJobService llmJobService;
    private final LlmJobStreamService llmJobStreamService;

    @GetMapping("/llm-jobs/{jobId}")
    @RewriteApi(
            id = "API-015",
            summary = "Job 상태 조회",
            tag = "LLM Job",
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
        return LlmJobStateResponse.from(llmJobService.findMyJob(jobId));
    }

    @GetMapping(path = "/llm-jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(
            responseCode = "200",
            description = "job.state와 도메인별 중간 결과 SSE 스트림",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(
                            type = "string",
                            example = """
                                    event: job.state
                                    data: {"jobType":"COVER_LETTER_REVIEW","status":"PROCESSING","progress":{"current":1,"total":3,"message":"첨삭 중"},"resultRef":null,"error":null}

                                    event: review.questions
                                    data: {"items":[{"questionId":"question_01","order":1,"aiReport":"분석","rewrittenAnswer":"수정 답변","rewrittenAnswerLength":5,"finalAnswer":"수정 답변","finalAnswerLength":5}]}
                                    """
                    )
            )
    )
    @RewriteApi(
            id = "API-016",
            summary = "Job 스트림",
            tag = "LLM Job",
            purpose = "첨삭·키워드 분석·AI 면접 Job의 상태와 도메인별 중간 결과를 하나의 SSE 연결로 수신한다.",
            screens = {"첨삭 진행", "키워드 분석", "AI 면접"},
            trigger = "Job 시작 API가 반환한 jobId로 진행 상태를 구독할 때 연결한다.",
            behavior = """
                    job.state 이벤트로 최초 상태, 진행률과 종료 상태를 전달한다.
                    첨삭 문항은 review.questions, 면접 피드백은 interview.feedback.delta 이벤트로 전달한다.
                    job.state.status=FAILED는 정상 SSE 연결에서 받은 비동기 작업 결과이며 HTTP ErrorResponse가 아니다.
                    """,
            success = "서버가 최종 job.state와 도메인별 후속 이벤트 전송을 마친 뒤 연결을 종료한다. 클라이언트는 terminal 상태에 따라 API별 결과 조회 또는 실패 처리를 수행한다.",
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
