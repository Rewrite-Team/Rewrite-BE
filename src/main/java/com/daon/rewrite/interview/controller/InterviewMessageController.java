package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.InterviewMessageListResponse;
import com.daon.rewrite.interview.dto.SendInterviewMessageRequest;
import com.daon.rewrite.interview.dto.SendInterviewMessageResponse;
import com.daon.rewrite.interview.service.InterviewMessageService;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewMessageController {

    private final InterviewMessageService interviewMessageService;

    @GetMapping("/interview-threads/{threadId}/messages")
    @RewriteApi(
            id = "API-029",
            summary = "대화 메시지 조회",
            tag = "AI 면접",
            purpose = "질문별 대화 메시지와 피드백 SSE 복구용 조건부 jobId를 조회한다.",
            screens = "AI 면접",
            trigger = "질문 대화 진입, 새로고침 또는 피드백 완료 후 확정 메시지를 조회할 때 호출한다.",
            behavior = "메시지를 표시 순서로 반환하고 진행·실패 피드백 Job이 있으면 jobId를 함께 반환한다. 실패는 HTTP 오류가 아닌 복구 상태다.",
            success = "메시지를 순서대로 표시하고 jobId가 있으면 API-016에 연결한다. 완료 후 다시 조회해 임시 delta를 확정 content·score로 교체한다.",
            errors = @ApiError(code = ErrorCode.NOT_FOUND, condition = "thread 없음·비소유 또는 삭제된 자기소개서에 연결됨", action = "대화 화면을 종료하고 API-025를 재조회한다.")
    )
    public InterviewMessageListResponse getInterviewMessages(@PathVariable String threadId) {
        return InterviewMessageListResponse.from(
                interviewMessageService.findMyInterviewMessages(threadId)
        );
    }

    @PostMapping("/interview-threads/{threadId}/messages")
    @RewriteApi(
            id = "API-023",
            summary = "사용자 답변 전송",
            tag = "AI 면접",
            purpose = "사용자 답변을 저장하고 실시간 피드백 생성 Job을 시작한다.",
            screens = "AI 면접",
            trigger = "사용자가 면접 답변 입력을 확정해 전송할 때 호출한다.",
            behavior = "검증된 USER 메시지를 저장하고 userMessageId·jobId를 반환한다. 피드백 delta와 최종 상태는 API-016으로 전달된다.",
            success = "jobId로 API-016에 연결해 delta를 표시하고 COMPLETED이면 API-029를 다시 조회해 저장된 결과로 교체한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "content 누락, trim 후 빈 값 또는 2000자 초과",
                            action = "답변 입력란에 details[field=content].reason을 표시한다.",
                            detailField = "content",
                            detailReason = "면접 답변은 최대 2000자까지 입력할 수 있습니다."
                    ),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "thread 없음·비소유 또는 삭제된 자기소개서에 연결됨", action = "대화 화면을 종료하고 API-025를 재조회한다."),
                    @ApiError(code = ErrorCode.LLM_JOB_ALREADY_RUNNING, condition = "같은 자기소개서에 다른 AI Job이 진행 중", action = "USER 메시지는 저장되지 않으며 기존 작업 완료 후 다시 전송하도록 안내한다.")
            }
    )
    public SendInterviewMessageResponse sendInterviewMessage(
            @PathVariable String threadId,
            @RequestBody(required = false) SendInterviewMessageRequest request
    ) {
        return SendInterviewMessageResponse.from(
                interviewMessageService.sendMyInterviewMessage(
                        threadId,
                        request == null ? null : request.content()
                )
        );
    }
}
