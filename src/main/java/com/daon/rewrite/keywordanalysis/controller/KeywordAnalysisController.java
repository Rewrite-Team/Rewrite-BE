package com.daon.rewrite.keywordanalysis.controller;

import com.daon.rewrite.keywordanalysis.dto.LatestKeywordAnalysisResponse;
import com.daon.rewrite.keywordanalysis.dto.StartKeywordAnalysisResponse;
import com.daon.rewrite.keywordanalysis.service.KeywordAnalysisService;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KeywordAnalysisController {

    private final KeywordAnalysisService keywordAnalysisService;

    @PostMapping("/cover-letters/{coverLetterId}/keyword-analysis")
    @RewriteApi(
            id = "API-020",
            summary = "키워드 분석 시작 또는 재분석",
            tag = "키워드 분석",
            purpose = "최신 성공 첨삭 버전으로 키워드 분석 Job을 시작한다.",
            screens = "키워드 분석",
            trigger = "사용자가 키워드 분석을 처음 시작하거나 실패 후 재분석할 때 호출한다.",
            behavior = "동일 키워드 분석 Job이 진행 중이면 기존 jobId를 반환하고 다른 종류의 AI Job과는 충돌한다.",
            success = "jobId로 API-016에 연결하고 COMPLETED이면 API-021을 다시 조회한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "대상 없음 안내 후 목록으로 이동한다."),
                    @ApiError(code = ErrorCode.CONFLICT, condition = "latestReviewedVersionId가 없어 성공한 첨삭 버전이 없음", action = "API-012를 재조회해 현재 상태 화면으로 전환한다."),
                    @ApiError(code = ErrorCode.LLM_JOB_ALREADY_RUNNING, condition = "키워드 분석 외 다른 AI Job이 진행 중", action = "다른 AI 작업 진행을 안내하고 자동 재시도하지 않는다.")
            }
    )
    public StartKeywordAnalysisResponse startKeywordAnalysis(@PathVariable String coverLetterId) {
        return StartKeywordAnalysisResponse.from(
                keywordAnalysisService.startMyKeywordAnalysis(coverLetterId)
        );
    }

    @GetMapping("/cover-letters/{coverLetterId}/keyword-analysis/latest")
    @RewriteApi(
            id = "API-021",
            summary = "최신 키워드 분석 조회",
            tag = "키워드 분석",
            purpose = "키워드 분석 화면 데이터와 SSE 복구용 조건부 jobId를 조회한다.",
            screens = "키워드 분석",
            trigger = "화면 진입·새로고침 또는 API-016 연결 실패 중 polling할 때 호출한다.",
            behavior = "NOT_STARTED·PROCESSING·COMPLETED·FAILED를 모두 200 정상 상태로 반환하고 PROCESSING일 때 jobId를 제공한다.",
            success = "PROCESSING이면 API-016에 연결하고 COMPLETED이면 결과를 표시하며 FAILED이면 API-020 재시도를 제공한다.",
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "자기소개서 없음·비소유·삭제",
                    action = "대상 없음 안내 후 목록으로 이동한다."
            )
    )
    public LatestKeywordAnalysisResponse getLatestKeywordAnalysis(@PathVariable String coverLetterId) {
        return LatestKeywordAnalysisResponse.from(
                keywordAnalysisService.findMyLatestKeywordAnalysis(coverLetterId)
        );
    }
}
