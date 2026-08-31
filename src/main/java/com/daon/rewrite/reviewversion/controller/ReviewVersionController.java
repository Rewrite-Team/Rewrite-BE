package com.daon.rewrite.reviewversion.controller;

import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.global.response.SuccessResponse;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import com.daon.rewrite.reviewversion.dto.ReviewVersionListResponse;
import com.daon.rewrite.reviewversion.dto.RequestReReviewRequest;
import com.daon.rewrite.reviewversion.dto.RequestReReviewResponse;
import com.daon.rewrite.reviewversion.dto.SaveFinalAnswersRequest;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.ReviewVersionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewVersionController {

    private final ReviewVersionQueryService reviewVersionQueryService;
    private final ReviewVersionCommandService reviewVersionCommandService;
    private final CoverLetterDetailQueryService coverLetterDetailQueryService;

    @GetMapping("/cover-letters/{coverLetterId}/review-versions")
    @RewriteApi(
            id = "API-017",
            summary = "첨삭 버전 목록 조회",
            tag = "첨삭 버전",
            purpose = "자기소개서의 성공한 첨삭 버전 목록을 과거부터 최신 순으로 조회한다.",
            screens = "첨삭 결과",
            trigger = "첨삭 결과 화면에서 버전 선택 목록을 구성하거나 완료 후 목록을 갱신할 때 호출한다.",
            behavior = "성공한 버전만 반환하며 isLatest로 최신 버전의 편집 가능 여부를 구분한다. 성공 버전이 없으면 빈 배열이다.",
            success = "선택한 versionId로 API-018을 조회하고 isLatest 버전에만 최종 작성본 편집을 제공한다.",
            errors = @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "대상 없음 안내 후 자기소개서 목록으로 이동한다.")
    )
    public ReviewVersionListResponse findReviewVersions(@PathVariable String coverLetterId) {
        return ReviewVersionListResponse.from(
                reviewVersionQueryService.findMyReviewVersions(coverLetterId)
        );
    }

    @PostMapping("/cover-letters/{coverLetterId}/review-versions")
    @RewriteApi(
            id = "API-024",
            summary = "AI 첨삭 다시받기",
            tag = "첨삭 버전",
            purpose = "최신 성공 버전의 최종 작성본을 기준으로 재첨삭 Job을 시작한다.",
            screens = {"첨삭 결과", "첨삭 진행"},
            trigger = "사용자가 재첨삭 요구사항을 확인하고 다시 첨삭받기를 요청할 때 호출한다.",
            behavior = "동일 재첨삭 Job이 진행 중이면 기존 jobId를 반환한다. Job 시작 후 AI 실패는 API-016·015의 FAILED 상태로 전달된다.",
            success = "displayStatus=REVIEWING을 반영하고 jobId로 API-016에 연결한다. 완료 후 API-017과 상세를 다시 조회한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "requestInstruction이 1000자 초과",
                            action = "입력란에 details[].reason을 표시한다.",
                            detailField = "requestInstruction",
                            detailReason = "재첨삭 요구사항은 최대 1000자까지 입력할 수 있습니다."
                    ),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "대상 없음 안내 후 목록으로 이동한다."),
                    @ApiError(code = ErrorCode.CONFLICT, condition = "latestReviewedVersionId가 없어 최신 성공 버전이 없음", action = "API-012를 재조회해 현재 상태 화면으로 전환한다."),
                    @ApiError(code = ErrorCode.LLM_JOB_ALREADY_RUNNING, condition = "재첨삭 외 다른 AI Job이 진행 중", action = "다른 AI 작업이 진행 중임을 안내하고 요청을 중단한다.")
            }
    )
    public RequestReReviewResponse requestReReview(
            @PathVariable String coverLetterId,
            @RequestBody(required = false) RequestReReviewRequest request
    ) {
        return RequestReReviewResponse.from(
                reviewVersionCommandService.requestMyReReview(
                        coverLetterId,
                        request == null ? null : request.requestInstruction()
                )
        );
    }

    @GetMapping("/cover-letters/{coverLetterId}/review-versions/{versionId}")
    @RewriteApi(
            id = "API-018",
            summary = "첨삭 버전 상세 조회",
            tag = "첨삭 버전",
            purpose = "선택한 성공 첨삭 버전의 자기소개서와 문항별 결과를 조회한다.",
            screens = "첨삭 결과",
            trigger = "사용자가 API-017 버전 목록에서 특정 버전을 선택할 때 호출한다.",
            behavior = "선택 버전의 aiReport·rewrittenAnswer·finalAnswer와 길이 필드를 공통 상세 구조로 반환한다.",
            success = "문항을 order로 정렬해 표시하고 최신 버전 여부에 따라 최종 작성본 편집을 제어한다.",
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "자기소개서·첨삭 버전 없음, 비소유·삭제 또는 버전 소속 불일치",
                    action = "버전 목록 또는 자기소개서 목록으로 이동한다."
            )
    )
    public CoverLetterDetailResponse findReviewVersion(
            @PathVariable String coverLetterId,
            @PathVariable String versionId
    ) {
        return CoverLetterDetailResponse.from(coverLetterDetailQueryService.findVersion(coverLetterId, versionId));
    }

    @PutMapping("/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers")
    @RewriteApi(
            id = "API-019",
            summary = "최종 작성본 일괄 저장",
            tag = "첨삭 버전",
            purpose = "최신 첨삭 버전의 모든 문항 최종 작성본을 한 번에 저장한다.",
            screens = "첨삭 결과",
            trigger = "사용자가 최신 버전의 최종 작성본 편집을 저장할 때 호출한다.",
            behavior = "모든 문항 포함·중복·소속·빈 값·길이를 검증하고 최신 버전에만 전체 replace로 저장한다.",
            success = "로컬 입력을 유지하고 서버 정규화 값을 다시 맞출 필요가 있으면 API-012 또는 API-018을 조회한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "문항 누락·중복·불일치, 빈 답변 또는 길이 위반",
                            action = "details[].field를 문항 입력이나 전체 answers 오류에 연결한다.",
                            detailField = "answers[0].finalAnswer",
                            detailReason = "최종 작성본은 최대 5000자까지 입력할 수 있습니다."
                    ),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서·버전 없음, 비소유·삭제", action = "입력값을 유지하고 대상 없음 안내 후 이전 화면으로 이동한다."),
                    @ApiError(code = ErrorCode.REVIEW_VERSION_NOT_LATEST, condition = "열린 버전이 더 이상 최신 버전이 아님", action = "자동 재전송하지 않고 API-012 또는 API-018로 최신 데이터를 조회한다.")
            }
    )
    public SuccessResponse saveFinalAnswers(
            @PathVariable String coverLetterId,
            @PathVariable String versionId,
            @RequestBody SaveFinalAnswersRequest request
    ) {
        reviewVersionCommandService.saveMyFinalAnswers(
                coverLetterId,
                versionId,
                request == null ? null : request.toInputs()
        );
        return SuccessResponse.completed();
    }
}
