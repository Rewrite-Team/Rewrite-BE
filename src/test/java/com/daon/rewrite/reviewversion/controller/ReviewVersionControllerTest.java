package com.daon.rewrite.reviewversion.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.service.RequestReReviewResult;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswersResult;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.ReviewVersionDetail;
import com.daon.rewrite.reviewversion.service.ReviewVersionQueryService;
import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewVersionController.class)
class ReviewVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewVersionQueryService reviewVersionQueryService;

    @MockitoBean
    private ReviewVersionCommandService reviewVersionCommandService;

    @Test
    void findReviewVersionsReturnsItems() throws Exception {
        CoverLetter coverLetter = CoverLetter.draft(
                "cl_1",
                "user_1",
                Instant.parse("2026-06-21T01:00:00Z")
        );
        ReviewVersion reviewVersion = ReviewVersion.first(
                "rv_1",
                coverLetter,
                Instant.parse("2026-06-21T05:00:00Z")
        );
        given(reviewVersionQueryService.findMyReviewVersions("cl_1"))
                .willReturn(List.of(new ReviewVersionSummary(reviewVersion, true)));

        mockMvc.perform(get("/cover-letters/{coverLetterId}/review-versions", "cl_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("rv_1"))
                .andExpect(jsonPath("$.items[0].version").value("v0.1"))
                .andExpect(jsonPath("$.items[0].isLatest").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-06-21T14:00:00"));
    }

    @Test
    void findReviewVersionReturnsQuestionResults() throws Exception {
        CoverLetter coverLetter = CoverLetter.draft(
                "cl_1",
                "user_1",
                Instant.parse("2026-06-21T01:00:00Z")
        );
        CoverLetterQuestion question = CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "지원 동기는?",
                1000,
                "원본 답변"
        );
        ReviewVersion reviewVersion = ReviewVersion.first(
                "rv_1",
                coverLetter,
                Instant.parse("2026-06-21T05:00:00Z")
        );
        ReviewVersionQuestionResult questionResult = ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                question,
                "AI 리포트",
                "수정 답변"
        );
        given(reviewVersionQueryService.findMyReviewVersion("cl_1", "rv_1"))
                .willReturn(new ReviewVersionDetail("cl_1", reviewVersion, true, List.of(questionResult)));

        mockMvc.perform(get(
                        "/cover-letters/{coverLetterId}/review-versions/{versionId}",
                        "cl_1",
                        "rv_1"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rv_1"))
                .andExpect(jsonPath("$.coverLetterId").value("cl_1"))
                .andExpect(jsonPath("$.version").value("v0.1"))
                .andExpect(jsonPath("$.isLatest").value(true))
                .andExpect(jsonPath("$.requestInstruction").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-06-21T14:00:00"))
                .andExpect(jsonPath("$.questionResults[0].questionResultId").value("rvqr_1"))
                .andExpect(jsonPath("$.questionResults[0].questionId").value("clq_1"))
                .andExpect(jsonPath("$.questionResults[0].order").value(1))
                .andExpect(jsonPath("$.questionResults[0].question").value("지원 동기는?"))
                .andExpect(jsonPath("$.questionResults[0].maxAnswerLength").value(1000))
                .andExpect(jsonPath("$.questionResults[0].originalAnswer").value("원본 답변"))
                .andExpect(jsonPath("$.questionResults[0].originalAnswerLength").value(5))
                .andExpect(jsonPath("$.questionResults[0].aiReport").value("AI 리포트"))
                .andExpect(jsonPath("$.questionResults[0].rewrittenAnswer").value("수정 답변"))
                .andExpect(jsonPath("$.questionResults[0].rewrittenAnswerLength").value(5))
                .andExpect(jsonPath("$.questionResults[0].finalAnswer").value("수정 답변"))
                .andExpect(jsonPath("$.questionResults[0].finalAnswerLength").value(5));
    }

    @Test
    void findReviewVersionReturnsRequestInstructionWhenPresent() throws Exception {
        CoverLetter coverLetter = CoverLetter.draft(
                "cl_1",
                "user_1",
                Instant.parse("2026-06-21T01:00:00Z")
        );
        ReviewVersion reviewVersion = ReviewVersion.first(
                "rv_1",
                coverLetter,
                Instant.parse("2026-06-21T05:00:00Z")
        );
        ReflectionTestUtils.setField(reviewVersion, "requestInstruction", "직무 키워드를 강조해주세요.");
        given(reviewVersionQueryService.findMyReviewVersion("cl_1", "rv_1"))
                .willReturn(new ReviewVersionDetail("cl_1", reviewVersion, true, List.of()));

        mockMvc.perform(get(
                        "/cover-letters/{coverLetterId}/review-versions/{versionId}",
                        "cl_1",
                        "rv_1"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestInstruction").value("직무 키워드를 강조해주세요."));
    }

    @Test
    void findReviewVersionsReturnsNotFound() throws Exception {
        given(reviewVersionQueryService.findMyReviewVersions("cl_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/cover-letters/{coverLetterId}/review-versions", "cl_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void findReviewVersionReturnsNotFound() throws Exception {
        given(reviewVersionQueryService.findMyReviewVersion("cl_1", "rv_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get(
                        "/cover-letters/{coverLetterId}/review-versions/{versionId}",
                        "cl_1",
                        "rv_missing"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Nested
    class SaveFinalAnswers {

        @Test
        void saveFinalAnswersReturnsUpdatedQuestionResults() throws Exception {
            CoverLetter coverLetter = CoverLetter.draft(
                    "cl_1",
                    "user_1",
                    Instant.parse("2026-06-21T01:00:00Z")
            );
            CoverLetterQuestion question = CoverLetterQuestion.create(
                    "clq_1",
                    coverLetter,
                    1,
                    "지원 동기는?",
                    1000,
                    "원본 답변"
            );
            ReviewVersion reviewVersion = ReviewVersion.first(
                    "rv_1",
                    coverLetter,
                    Instant.parse("2026-06-21T05:00:00Z")
            );
            ReviewVersionQuestionResult questionResult = ReviewVersionQuestionResult.create(
                    "rvqr_1",
                    reviewVersion,
                    question,
                    "AI 리포트",
                    "수정 답변"
            );
            questionResult.updateFinalAnswer("최종 답변😀");
            given(reviewVersionCommandService.saveMyFinalAnswers(
                    "cl_1",
                    "rv_1",
                    List.of(new SaveFinalAnswerInput("rvqr_1", " 최종 답변😀 "))
            )).willReturn(new SaveFinalAnswersResult(
                    "cl_1",
                    "rv_1",
                    List.of(questionResult),
                    Instant.parse("2026-06-21T05:40:00Z")
            ));

            mockMvc.perform(put(
                            "/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers",
                            "cl_1",
                            "rv_1"
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answers": [
                                        {
                                          "questionResultId": "rvqr_1",
                                          "finalAnswer": " 최종 답변😀 "
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverLetterId").value("cl_1"))
                    .andExpect(jsonPath("$.reviewVersionId").value("rv_1"))
                    .andExpect(jsonPath("$.questionResults[0].questionResultId").value("rvqr_1"))
                    .andExpect(jsonPath("$.questionResults[0].finalAnswer").value("최종 답변😀"))
                    .andExpect(jsonPath("$.questionResults[0].finalAnswerLength").value(6))
                    .andExpect(jsonPath("$.updatedAt").value("2026-06-21T14:40:00"));
        }

        @Test
        void saveFinalAnswersReturnsConflictWhenVersionIsNotLatest() throws Exception {
            given(reviewVersionCommandService.saveMyFinalAnswers(
                    "cl_1",
                    "rv_old",
                    List.of(new SaveFinalAnswerInput("rvqr_1", "최종 답변"))
            )).willThrow(new BusinessException(ErrorCode.REVIEW_VERSION_NOT_LATEST));

            mockMvc.perform(put(
                            "/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers",
                            "cl_1",
                            "rv_old"
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answers": [
                                        {
                                          "questionResultId": "rvqr_1",
                                          "finalAnswer": "최종 답변"
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("REVIEW_VERSION_NOT_LATEST"));
        }

        @Test
        void saveFinalAnswersReturnsValidationDetails() throws Exception {
            given(reviewVersionCommandService.saveMyFinalAnswers(
                    "cl_1",
                    "rv_1",
                    List.of(new SaveFinalAnswerInput("rvqr_1", " "))
            )).willThrow(new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new ErrorResponse.ErrorDetail(
                            "answers[0].finalAnswer",
                            "최종 작성본을 입력해야 합니다."
                    ))
            ));

            mockMvc.perform(put(
                            "/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers",
                            "cl_1",
                            "rv_1"
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answers": [
                                        {
                                          "questionResultId": "rvqr_1",
                                          "finalAnswer": " "
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("answers[0].finalAnswer"));
        }
    }

    @Nested
    class RequestReReview {

        @Test
        void requestReReviewReturnsPendingJob() throws Exception {
            LlmJob job = LlmJob.pendingReReview(
                    "job_1",
                    "cl_1",
                    "직무 키워드를 강조해주세요.",
                    "rv_1",
                    Instant.parse("2026-06-21T05:50:00Z"),
                    2
            );
            given(reviewVersionCommandService.requestMyReReview(
                    "cl_1",
                    " 직무 키워드를 강조해주세요. "
            )).willReturn(new RequestReReviewResult("cl_1", job));

            mockMvc.perform(post("/cover-letters/{coverLetterId}/review-versions", "cl_1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "requestInstruction": " 직무 키워드를 강조해주세요. "
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value("job_1"))
                    .andExpect(jsonPath("$.coverLetterId").value("cl_1"))
                    .andExpect(jsonPath("$.coverLetterStatus").value("REVIEWED"))
                    .andExpect(jsonPath("$.jobStatus").value(LlmJobStatus.PENDING.name()));
        }

        @Test
        void requestReReviewReturnsValidationDetails() throws Exception {
            given(reviewVersionCommandService.requestMyReReview(
                    "cl_1",
                    "가".repeat(1001)
            )).willThrow(new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new ErrorResponse.ErrorDetail(
                            "requestInstruction",
                            "재첨삭 요구사항은 최대 1000자까지 입력할 수 있습니다."
                    ))
            ));

            mockMvc.perform(post("/cover-letters/{coverLetterId}/review-versions", "cl_1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "requestInstruction": "%s"
                                    }
                                    """.formatted("가".repeat(1001))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("requestInstruction"));
        }

        @Test
        void requestReReviewReturnsConflictWhenJobAlreadyRunning() throws Exception {
            given(reviewVersionCommandService.requestMyReReview("cl_1", null))
                    .willThrow(new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING));

            mockMvc.perform(post("/cover-letters/{coverLetterId}/review-versions", "cl_1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("LLM_JOB_ALREADY_RUNNING"));
        }
    }
}
