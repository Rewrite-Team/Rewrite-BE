package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.dto.CreateCoverLetterResponse;
import com.daon.rewrite.coverletter.dto.SaveBasicInfoRequest;
import com.daon.rewrite.coverletter.dto.SavePreferencesRequest;
import com.daon.rewrite.coverletter.dto.SaveQuestionsRequest;
import com.daon.rewrite.coverletter.dto.SubmitCoverLetterResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.CoverLetterReviewStatusStreamService;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import com.daon.rewrite.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;
    private final CoverLetterReviewStatusStreamService coverLetterReviewStatusStreamService;
    private final CoverLetterDetailQueryService coverLetterDetailQueryService;

    @PostMapping("/cover-letters")
    @ResponseStatus(HttpStatus.CREATED)
    @RewriteApi(
            id = "API-008",
            summary = "자기소개서 생성",
            tag = "자기소개서",
            purpose = "새 WRITING 자기소개서 초안을 생성한다.",
            screens = "자기소개서 등록",
            trigger = "사용자가 새 자기소개서 등록을 시작할 때 호출한다.",
            behavior = "빈 초안을 현재 사용자 소유로 생성하며 입력 데이터는 후속 API-009~011로 저장한다.",
            success = "반환된 coverLetterId로 자기소개서 등록 1단계 화면을 연다.",
            successStatus = 201,
            csrfProtected = true
    )
    public CreateCoverLetterResponse create() {
        return CreateCoverLetterResponse.from(coverLetterService.create());
    }

    /**
     * 현재 사용자의 자기소개서 목록을 조회
     *
     * @param page 1부터 시작하는 페이지 번호. 생략하면 1
     * @param size 페이지당 항목 수. 생략하면 9
     * @return 자기소개서 목록과 페이지 정보
     */
    @GetMapping("/cover-letters")
    @RewriteApi(
            id = "API-007",
            summary = "내 자기소개서 목록 조회",
            tag = "자기소개서",
            purpose = "현재 사용자의 모든 상태 자기소개서를 페이지 단위로 조회한다.",
            screens = "자기소개서 목록",
            trigger = "목록 화면 진입, 페이지 이동 또는 상태 복구가 필요할 때 호출한다.",
            behavior = "page는 1부터 시작하고 size는 1~9다. 저장된 상태를 displayStatus로 반환하며 전체 페이지를 넘으면 빈 목록이다.",
            success = "displayStatus에 따라 작성·진행·완료·실패 카드를 표시한다.",
            errors = @ApiError(
                    code = ErrorCode.VALIDATION_ERROR,
                    condition = "page < 1 또는 size가 1~9 범위를 벗어남",
                    action = "page=1, size=9로 정규화한 뒤 한 번 다시 조회한다."
            )
    )
    public CoverLetterListResponse findMyCoverLetters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        Page<CoverLetter> result = coverLetterService.findMyCoverLetters(page, size);
        return CoverLetterListResponse.from(result, page, size);
    }

    @GetMapping("/cover-letters/{coverLetterId}")
    @RewriteApi(
            id = "API-012",
            summary = "자기소개서 상세 조회",
            tag = "자기소개서",
            purpose = "미완성 WRITING 입력과 자기소개서 상세·첨삭 상태를 공통 구조로 조회한다.",
            screens = {"자기소개서 목록", "자기소개서 등록", "첨삭 결과"},
            trigger = "목록에서 항목을 열거나 등록·첨삭 화면을 새로고침하고 상태를 복구할 때 호출한다.",
            behavior = "displayStatus에 따라 nullable 등록 데이터, 최신 성공 버전, 진행·실패 Job과 성공한 부분 문항을 조건부 반환한다.",
            success = "WRITING은 등록 폼을 복구하고 REVIEWING은 API-016에 연결하며 REVIEWED·REVIEW_FAILED는 결과 또는 실패 화면을 구성한다.",
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "자기소개서 없음·비소유·삭제",
                    action = "대상이 없거나 접근할 수 없음을 안내하고 목록으로 이동한다."
            )
    )
    public CoverLetterDetailResponse findMyCoverLetter(@PathVariable String coverLetterId) {
        return CoverLetterDetailResponse.from(coverLetterDetailQueryService.findCurrent(coverLetterId));
    }

    @DeleteMapping("/cover-letters/{coverLetterId}")
    @RewriteApi(
            id = "API-013",
            summary = "자기소개서 삭제",
            tag = "자기소개서",
            purpose = "현재 사용자가 소유한 자기소개서를 삭제한다.",
            screens = "자기소개서 목록",
            trigger = "사용자가 목록에서 삭제를 확인할 때 호출한다.",
            behavior = "자기소개서를 삭제하고 진행 중인 첨삭 Job이 있으면 CANCELED로 전환한다.",
            success = "목록에서 항목을 제거하거나 API-007을 다시 조회한다.",
            csrfProtected = true,
            errors = @ApiError(
                    code = ErrorCode.NOT_FOUND,
                    condition = "자기소개서 없음·비소유·이미 삭제",
                    action = "이미 없는 대상으로 보고 목록에서 제거하며 실패 토스트는 표시하지 않는다."
            )
    )
    public SuccessResponse deleteMyCoverLetter(@PathVariable String coverLetterId) {
        coverLetterService.deleteMyCoverLetter(coverLetterId);
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/basic-info")
    @RewriteApi(
            id = "API-009",
            summary = "기본 정보 저장",
            tag = "자기소개서",
            purpose = "자기소개서 등록 1단계의 미완성 기본 정보를 임시저장한다.",
            screens = "자기소개서 등록",
            trigger = "기본 정보 입력 변경 후 자동 저장할 때 호출한다.",
            behavior = """
                    현재 폼 전체를 WRITING 스냅샷으로 교체 저장한다.
                    누락·null·trim 후 빈 문자열은 null로 정규화하고, 값이 있는 필드만 길이와 URL 형식을 검증한다.
                    """,
            success = "임시저장 완료로 처리하며 다음 단계 이동 여부는 프론트엔드가 현재 입력값으로 판단한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "입력된 기본 정보의 최대 길이 또는 URL 형식 위반",
                            action = "details[].field와 reason을 해당 입력에 표시한다.",
                            detailField = "jobPostingUrl",
                            detailReason = "공고 링크 형식이 올바르지 않습니다."
                    ),
                    @ApiError(
                            code = ErrorCode.NOT_FOUND,
                            condition = "자기소개서 없음·비소유·삭제",
                            action = "자동저장을 중단하고 목록으로 이동한다."
                    ),
                    @ApiError(
                            code = ErrorCode.COVER_LETTER_NOT_WRITING,
                            condition = "자기소개서가 WRITING 상태가 아님",
                            action = "대기 중 자동저장을 폐기하고 API-012를 재조회해 현재 상태 화면으로 전환한다."
                    )
            }
    )
    public SuccessResponse saveBasicInfo(
            @PathVariable String coverLetterId,
            @RequestBody SaveBasicInfoRequest request
    ) {
        coverLetterService.saveBasicInfo(
                coverLetterId,
                request.title(),
                request.companyName(),
                request.positionTitle(),
                request.jobPostingUrl()
        );
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/preferences")
    @RewriteApi(
            id = "API-010",
            summary = "채용 우대사항 저장",
            tag = "자기소개서",
            purpose = "자기소개서 등록 2단계의 미완성 우대사항을 임시저장한다.",
            screens = "자기소개서 등록",
            trigger = "우대사항 입력 변경 후 자동 저장할 때 호출한다.",
            behavior = "현재 입력 전체를 WRITING 스냅샷으로 교체하고 누락·null·빈 문자열은 null로 정규화한다.",
            success = "임시저장 완료로 처리하며 다음 단계 이동 여부는 프론트엔드가 판단한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "preferences 최대 3000자 위반",
                            action = "해당 입력에 details[].reason을 표시한다.",
                            detailField = "preferences",
                            detailReason = "채용 우대사항은 최대 3000자까지 입력할 수 있습니다."
                    ),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "자동저장을 중단하고 목록으로 이동한다."),
                    @ApiError(code = ErrorCode.COVER_LETTER_NOT_WRITING, condition = "자기소개서가 WRITING 상태가 아님", action = "대기 중 자동저장을 폐기하고 API-012를 재조회한다.")
            }
    )
    public SuccessResponse savePreferences(
            @PathVariable String coverLetterId,
            @RequestBody SavePreferencesRequest request
    ) {
        coverLetterService.savePreferences(
                coverLetterId,
                request.preferences()
        );
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/questions")
    @RewriteApi(
            id = "API-011",
            summary = "질문과 답변 저장",
            tag = "자기소개서",
            purpose = "자기소개서 등록 3단계의 미완성 질문과 답변을 임시저장한다.",
            screens = "자기소개서 등록",
            trigger = "문항 입력 변경 후 자동 저장할 때 호출한다.",
            behavior = "현재 문항 배열 전체를 순서대로 WRITING 스냅샷으로 교체하며 nullable 입력은 제출 전까지 허용한다.",
            success = "임시저장 완료로 처리하고 제출 가능 여부는 프론트엔드가 현재 입력값으로 판단한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "null 문항 객체 또는 문항 필드 길이·범위 위반",
                            action = "questions[index].field를 해당 문항 입력에 연결한다.",
                            detailField = "questions[0].maxAnswerLength",
                            detailReason = "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
                    ),
                    @ApiError(code = ErrorCode.NOT_FOUND, condition = "자기소개서 없음·비소유·삭제", action = "자동저장을 중단하고 목록으로 이동한다."),
                    @ApiError(code = ErrorCode.COVER_LETTER_NOT_WRITING, condition = "자기소개서가 WRITING 상태가 아님", action = "대기 중 자동저장을 폐기하고 API-012를 재조회한다.")
            }
    )
    public SuccessResponse saveQuestions(
            @PathVariable String coverLetterId,
            @RequestBody SaveQuestionsRequest request
    ) {
        coverLetterService.saveQuestions(
                coverLetterId,
                request.toInputs()
        );
        return SuccessResponse.completed();
    }

    @PostMapping("/cover-letters/{coverLetterId}/submit")
    @RewriteApi(
            id = "API-014",
            summary = "자기소개서 제출 및 최초 AI 첨삭 요청",
            tag = "자기소개서",
            purpose = "임시저장된 자기소개서의 필수값을 최종 검증하고 최초 AI 첨삭 Job을 시작한다.",
            screens = {"자기소개서 등록", "첨삭 진행"},
            trigger = "등록 4단계에서 제출을 확정하거나 최초 첨삭 실패 화면에서 수동 재시도할 때 호출한다.",
            behavior = """
                    WRITING 또는 최초 첨삭 실패 상태의 저장 데이터를 최종 검증한다.
                    새 Job을 만들거나 동일 최초 첨삭 Job이 진행 중이면 기존 jobId를 반환한다.
                    200 OK 이후의 AI 처리 실패는 HTTP 오류가 아니라 API-016의 job.state.status=FAILED로 전달된다.
                    """,
            success = "displayStatus=REVIEWING이면 jobId로 API-016 SSE 연결을 시작한다. REVIEWED이면 jobId=null이며 API-012를 조회해 기존 결과 화면으로 전환한다.",
            csrfProtected = true,
            errors = {
                    @ApiError(
                            code = ErrorCode.VALIDATION_ERROR,
                            condition = "제출 필수값 누락 또는 길이·범위 위반",
                            action = "details[].field를 등록 단계에 매핑하고 최초 오류가 있는 단계로 이동한다.",
                            detailField = "preferences",
                            detailReason = "채용 우대사항을 입력해야 합니다."
                    ),
                    @ApiError(
                            code = ErrorCode.NOT_FOUND,
                            condition = "자기소개서 없음·비소유·삭제",
                            action = "대상 없음 안내 후 목록으로 이동한다."
                    ),
                    @ApiError(
                            code = ErrorCode.CONFLICT,
                            condition = "재첨삭 실패로 기존 성공 버전이 남아 있어 최초 제출 API를 사용할 수 없음",
                            action = "API-012로 기존 결과를 조회하고 재첨삭은 API-024로 요청한다."
                    ),
                    @ApiError(
                            code = ErrorCode.LLM_JOB_ALREADY_RUNNING,
                            condition = "최초 첨삭 외 다른 AI Job이 진행 중",
                            action = "새 요청을 중단하고 제출 화면을 유지하며 자동 재시도하지 않는다."
                    )
            }
    )
    public SubmitCoverLetterResponse submit(@PathVariable String coverLetterId) {
        SubmitCoverLetterResult result = coverLetterService.submit(coverLetterId);
        return SubmitCoverLetterResponse.from(result);
    }

    @GetMapping(path = "/cover-letters/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(
            responseCode = "200",
            description = "현재 사용자의 자기소개서 상태 스냅샷과 변경 SSE 스트림",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(
                            type = "string",
                            example = """
                                    event: cover-letter.review-status.snapshot
                                    data: {"items":[{"coverLetterId":"cover_letter_01","displayStatus":"REVIEWING","latestReviewedVersionId":null,"jobId":"job_01"}]}

                                    event: cover-letter.review-status.changed
                                    data: {"coverLetterId":"cover_letter_01","displayStatus":"REVIEWED","latestReviewedVersionId":"review_version_01","jobId":null}
                                    """
                    )
            )
    )
    @RewriteApi(
            id = "API-030",
            summary = "내 자기소개서 첨삭 상태 스트림",
            tag = "자기소개서",
            purpose = "현재 사용자의 자기소개서 첨삭 상태 변경을 하나의 SSE 연결로 수신한다.",
            screens = {"자기소개서 목록", "첨삭 진행"},
            trigger = "로그인 후 목록·첨삭 진행 화면에서 사용자 단일 EventSource를 만들 때 연결한다.",
            behavior = "cover-letter.review-status.snapshot을 먼저 보내고 이후 cover-letter.review-status.changed 단건 이벤트를 전달한다. 재연결하면 전체 스냅샷으로 복구한다.",
            success = "스냅샷과 변경 이벤트를 displayStatus 기준으로 반영한다. 연결 오류는 API-004 인증 갱신 후 재연결하고 반복 실패 시 API-007을 조회한다."
    )
    public SseEmitter streamCoverLetterReviewStatuses() {
        return coverLetterReviewStatusStreamService.openMyStream();
    }
}
