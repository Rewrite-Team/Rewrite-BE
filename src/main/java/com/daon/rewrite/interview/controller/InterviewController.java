package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.AddInterviewQuestionResponse;
import com.daon.rewrite.interview.dto.CurrentInterviewResponse;
import com.daon.rewrite.interview.dto.InterviewQuestionListResponse;
import com.daon.rewrite.interview.dto.StartInterviewResponse;
import com.daon.rewrite.interview.service.InterviewService;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/cover-letters/{coverLetterId}/interview")
    @RewriteApi(
            id = "API-025",
            summary = "현재 면접 세션 조회",
            tag = "AI 면접",
            purpose = "자기소개서 요약, 현재 면접 세션과 미해결 질문 생성 Job을 조회한다.",
            screens = "AI 면접",
            trigger = "면접 화면 진입·새로고침 또는 SSE 상태 복구 시 호출한다.",
            behavior = "세션 없음과 FAILED도 200 정상 응답이며 진행 중 초기·추가 질문 생성이 있으면 조건부 jobId를 반환한다.",
            success = "jobId가 있으면 API-016에 연결하고 ACTIVE이면 기존 질문·대화 화면을 표시한다.",
            errors = @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "대상 없음 안내 후 목록으로 이동한다.")
    )
    public CurrentInterviewResponse getCurrentInterview(@PathVariable String coverLetterId) {
        return CurrentInterviewResponse.from(interviewService.findMyCurrentInterview(coverLetterId));
    }

    @GetMapping("/interviews/{interviewSessionId}/questions")
    @RewriteApi(
            id = "API-026",
            summary = "면접 질문 목록 조회",
            tag = "AI 면접",
            purpose = "최신 면접 질문부터 cursor 기반으로 조회하고 질문별 대화 진입 정보를 반환한다.",
            screens = "AI 면접",
            trigger = "면접 질문 화면 진입, 무한 스크롤 또는 추가 질문 생성 완료 후 호출한다.",
            behavior = "최초 요청은 cursor를 생략하고 이후 서버가 반환한 nextCursor만 사용한다. size는 1~20이다.",
            success = "응답 순서대로 질문을 추가하고 nextCursor=null이면 조회를 종료하며 threadId로 API-029를 연다.",
            errors = {
                    @ApiError(code = ErrorCode.VALIDATION_ERROR, condition = "cursor 형식 오류 또는 size가 1~20 범위를 벗어남", action = "목록 추가를 중단하고 최초 요청 또는 서버가 반환한 nextCursor로 다시 조회한다."),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "면접 세션 없음·비소유 또는 삭제된 자기소개서의 세션", action = "면접 화면을 종료하고 API-025를 재조회한다.")
            }
    )
    public InterviewQuestionListResponse getInterviewQuestions(
            @PathVariable String interviewSessionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        return InterviewQuestionListResponse.from(
                interviewService.findMyInterviewQuestions(interviewSessionId, cursor, size)
        );
    }

    @PostMapping("/cover-letters/{coverLetterId}/interviews")
    @RewriteApi(
            id = "API-022",
            summary = "모의면접 시작",
            tag = "AI 면접",
            purpose = "최신 성공 첨삭 버전으로 초기 면접 질문 생성 Job을 시작한다.",
            screens = "AI 면접",
            trigger = "사용자가 모의면접 시작 또는 초기 질문 생성 실패 후 재시도를 선택할 때 호출한다.",
            behavior = "동일 초기 질문 Job이 진행 중이면 기존 jobId를 반환하고 다른 AI Job과는 충돌한다.",
            success = "jobId로 API-016에 연결하고 COMPLETED이면 API-025와 API-026을 다시 조회한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "대상 없음 안내 후 목록으로 이동한다."),
                    @ApiError(code = ErrorCode.CONFLICT, condition = "latestReviewedVersionId가 없어 성공한 첨삭 버전이 없음", action = "API-012를 재조회해 현재 상태 화면으로 전환한다."),
                    @ApiError(code = ErrorCode.LLM_JOB_ALREADY_RUNNING, condition = "초기 질문 생성 외 다른 AI Job이 진행 중", action = "다른 AI 작업 진행을 안내하고 자동 재시도하지 않는다.")
            }
    )
    public StartInterviewResponse startInterview(@PathVariable String coverLetterId) {
        return StartInterviewResponse.from(interviewService.startMyInterview(coverLetterId));
    }

    @PostMapping("/interviews/{interviewSessionId}/questions")
    @RewriteApi(
            id = "API-027",
            summary = "면접 질문 추가 생성",
            tag = "AI 면접",
            purpose = "활성 면접 세션에 추가 질문 생성 Job을 시작한다.",
            screens = "AI 면접",
            trigger = "사용자가 추가 질문 생성을 요청하거나 실패 후 수동 재시도할 때 호출한다.",
            behavior = "동일 추가 질문 Job은 기존 jobId를 반환하고 세션 상태·성공 첨삭 버전·다른 AI Job 진행 여부를 검증한다.",
            success = "jobId로 API-016에 연결하고 COMPLETED이면 API-026을 다시 조회해 새 질문을 반영한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "세션 없음·비소유 또는 삭제된 자기소개서의 세션", action = "API-025를 재조회한다."),
                    @ApiError(code = ErrorCode.CONFLICT, condition = "세션이 ACTIVE가 아니거나 성공한 첨삭 버전이 없음", action = "API-025를 재조회하고 가능한 동작만 활성화한다."),
                    @ApiError(code = ErrorCode.LLM_JOB_ALREADY_RUNNING, condition = "다른 종류의 AI Job이 진행 중", action = "다른 AI 작업 진행을 안내하고 자동 재시도하지 않는다.")
            }
    )
    public AddInterviewQuestionResponse addInterviewQuestion(@PathVariable String interviewSessionId) {
        return AddInterviewQuestionResponse.from(
                interviewService.addMyInterviewQuestion(interviewSessionId)
        );
    }
}
