package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.coverletter.service.SaveQuestionsResult;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.llmjob.entity.LlmJob;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(CoverLetterController.class)
class CoverLetterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoverLetterService coverLetterService;

    @Test
    void createDraftReturnsCreatedDraft() throws Exception {
        CoverLetter draft = CoverLetter.draft(
                "cl_fixed",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        given(coverLetterService.createDraft()).willReturn(draft);

        mockMvc.perform(post("/cover-letters"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(draft.getId()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value("2026-06-20T14:00:00"));
    }

    @Test
    void findMyCoverLettersReturnsPagedItems() throws Exception {
        CoverLetter coverLetter = CoverLetter.draft(
                "cl_1",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        coverLetter.fillBasicInfo(
                "2026 상반기 백엔드 개발자 자기소개서",
                "Rewrite Corp",
                "백엔드 개발자",
                null,
                Instant.parse("2026-06-20T05:00:00Z")
        );
        coverLetter.setLatestReviewVersionId("rv_1");
        Page<CoverLetter> page = new PageImpl<>(
                List.of(coverLetter),
                PageRequest.of(0, 9),
                1
        );
        given(coverLetterService.findMyCoverLetters(1, 9, CoverLetterStatus.DRAFT)).willReturn(page);

        mockMvc.perform(get("/cover-letters")
                        .param("page", "1")
                        .param("size", "9")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("cl_1"))
                .andExpect(jsonPath("$.items[0].title").value("2026 상반기 백엔드 개발자 자기소개서"))
                .andExpect(jsonPath("$.items[0].companyName").value("Rewrite Corp"))
                .andExpect(jsonPath("$.items[0].positionTitle").value("백엔드 개발자"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-06-20T14:00:00"))
                .andExpect(jsonPath("$.items[0].latestReviewVersionId").value("rv_1"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(9))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findMyCoverLettersUsesDefaultPageAndSize() throws Exception {
        given(coverLetterService.findMyCoverLetters(1, 9, null))
                .willReturn(Page.empty(PageRequest.of(0, 9)));

        mockMvc.perform(get("/cover-letters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(9));
    }

    @Test
    void findMyCoverLettersRejectsInvalidPage() throws Exception {
        given(coverLetterService.findMyCoverLetters(0, 9, null))
                .willThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/cover-letters").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void findMyCoverLettersRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/cover-letters").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteMyCoverLetterReturnsDeletedAt() throws Exception {
        CoverLetter deleted = CoverLetter.draft(
                "cl_delete",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        deleted.markDeleted(Instant.parse("2026-06-20T06:00:00Z"));
        given(coverLetterService.deleteMyCoverLetter("cl_delete")).willReturn(deleted);

        mockMvc.perform(delete("/cover-letters/{coverLetterId}", "cl_delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.deletedAt").value("2026-06-20T15:00:00"));
    }

    @Test
    void deleteMyCoverLetterReturnsNotFound() throws Exception {
        given(coverLetterService.deleteMyCoverLetter("cl_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(delete("/cover-letters/{coverLetterId}", "cl_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void saveBasicInfoReturnsUpdatedBasicInfo() throws Exception {
        CoverLetter updated = CoverLetter.draft(
                "cl_basic",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        updated.fillBasicInfo(
                "2026 상반기 백엔드 개발자 자기소개서",
                "Rewrite Corp",
                "백엔드 개발자",
                "https://example.com/jobs/1",
                Instant.parse("2026-06-20T05:05:00Z")
        );
        given(coverLetterService.saveBasicInfo(
                "cl_basic",
                " 2026 상반기 백엔드 개발자 자기소개서 ",
                " Rewrite Corp ",
                " 백엔드 개발자 ",
                " https://example.com/jobs/1 "
        )).willReturn(updated);

        mockMvc.perform(put("/cover-letters/{coverLetterId}/basic-info", "cl_basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " 2026 상반기 백엔드 개발자 자기소개서 ",
                                  "companyName": " Rewrite Corp ",
                                  "positionTitle": " 백엔드 개발자 ",
                                  "jobPostingUrl": " https://example.com/jobs/1 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cl_basic"))
                .andExpect(jsonPath("$.title").value("2026 상반기 백엔드 개발자 자기소개서"))
                .andExpect(jsonPath("$.companyName").value("Rewrite Corp"))
                .andExpect(jsonPath("$.positionTitle").value("백엔드 개발자"))
                .andExpect(jsonPath("$.jobPostingUrl").value("https://example.com/jobs/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-20T14:05:00"));
    }

    @Test
    void saveBasicInfoReturnsValidationDetails() throws Exception {
        given(coverLetterService.saveBasicInfo(
                "cl_basic",
                " ",
                "Rewrite Corp",
                "백엔드 개발자",
                "not-a-url"
        )).willThrow(new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                List.of(
                        new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                                "title",
                                "자기소개서 제목은 필수입니다."
                        ),
                        new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                                "jobPostingUrl",
                                "공고 링크 형식이 올바르지 않습니다."
                        )
                )
        ));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/basic-info", "cl_basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "companyName": "Rewrite Corp",
                                  "positionTitle": "백엔드 개발자",
                                  "jobPostingUrl": "not-a-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("title"))
                .andExpect(jsonPath("$.error.details[1].field").value("jobPostingUrl"));
    }

    @Test
    void saveBasicInfoReturnsNotFound() throws Exception {
        given(coverLetterService.saveBasicInfo(
                "cl_missing",
                "제목",
                "회사",
                "직무",
                null
        )).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/basic-info", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "제목",
                                  "companyName": "회사",
                                  "positionTitle": "직무"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void saveBasicInfoReturnsCoverLetterNotDraft() throws Exception {
        given(coverLetterService.saveBasicInfo(
                "cl_reviewing",
                "제목",
                "회사",
                "직무",
                null
        )).willThrow(new BusinessException(ErrorCode.COVER_LETTER_NOT_DRAFT));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/basic-info", "cl_reviewing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "제목",
                                  "companyName": "회사",
                                  "positionTitle": "직무"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COVER_LETTER_NOT_DRAFT"));
    }

    @Test
    void savePreferencesReturnsUpdatedPreferences() throws Exception {
        CoverLetter updated = CoverLetter.draft(
                "cl_preferences",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        updated.fillPreferences(
                "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
                Instant.parse("2026-06-20T05:08:00Z")
        );
        given(coverLetterService.savePreferences(
                "cl_preferences",
                " Spring Boot 경험, 대용량 트래픽 처리 경험 우대 "
        )).willReturn(updated);

        mockMvc.perform(put("/cover-letters/{coverLetterId}/preferences", "cl_preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": " Spring Boot 경험, 대용량 트래픽 처리 경험 우대 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cl_preferences"))
                .andExpect(jsonPath("$.preferences").value("Spring Boot 경험, 대용량 트래픽 처리 경험 우대"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-20T14:08:00"));
    }

    @Test
    void savePreferencesReturnsValidationDetails() throws Exception {
        given(coverLetterService.savePreferences(
                "cl_preferences",
                " "
        )).willThrow(new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                        "preferences",
                        "채용 우대사항은 필수입니다."
                ))
        ));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/preferences", "cl_preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("preferences"));
    }

    @Test
    void savePreferencesReturnsNotFound() throws Exception {
        given(coverLetterService.savePreferences(
                "cl_missing",
                "Spring Boot 경험"
        )).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/preferences", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": "Spring Boot 경험"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void savePreferencesReturnsCoverLetterNotDraft() throws Exception {
        given(coverLetterService.savePreferences(
                "cl_reviewing",
                "Spring Boot 경험"
        )).willThrow(new BusinessException(ErrorCode.COVER_LETTER_NOT_DRAFT));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/preferences", "cl_reviewing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": "Spring Boot 경험"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COVER_LETTER_NOT_DRAFT"));
    }

    @Test
    void saveQuestionsReturnsSavedQuestions() throws Exception {
        CoverLetter updated = CoverLetter.draft(
                "cl_questions",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        updated.touch(Instant.parse("2026-06-20T05:10:00Z"));
        List<CoverLetterQuestion> questions = List.of(
                CoverLetterQuestion.create(
                        "clq_1",
                        updated,
                        1,
                        "지원 동기를 작성해주세요.",
                        1000,
                        "제가 지원한 이유는..."
                ),
                CoverLetterQuestion.create(
                        "clq_2",
                        updated,
                        2,
                        "직무 관련 경험을 작성해주세요.",
                        1500,
                        "저는 프로젝트에서..."
                )
        );
        given(coverLetterService.saveQuestions(
                "cl_questions",
                List.of(
                        new SaveQuestionInput(" 지원 동기를 작성해주세요. ", 1000, " 제가 지원한 이유는... "),
                        new SaveQuestionInput(" 직무 관련 경험을 작성해주세요. ", 1500, " 저는 프로젝트에서... ")
                )
        )).willReturn(new SaveQuestionsResult(updated, questions));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/questions", "cl_questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": [
                                    {
                                      "question": " 지원 동기를 작성해주세요. ",
                                      "maxAnswerLength": 1000,
                                      "originalAnswer": " 제가 지원한 이유는... "
                                    },
                                    {
                                      "question": " 직무 관련 경험을 작성해주세요. ",
                                      "maxAnswerLength": 1500,
                                      "originalAnswer": " 저는 프로젝트에서... "
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetterId").value("cl_questions"))
                .andExpect(jsonPath("$.questions[0].id").value("clq_1"))
                .andExpect(jsonPath("$.questions[0].order").value(1))
                .andExpect(jsonPath("$.questions[0].question").value("지원 동기를 작성해주세요."))
                .andExpect(jsonPath("$.questions[0].maxAnswerLength").value(1000))
                .andExpect(jsonPath("$.questions[0].originalAnswer").value("제가 지원한 이유는..."))
                .andExpect(jsonPath("$.questions[1].id").value("clq_2"))
                .andExpect(jsonPath("$.questions[1].order").value(2))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-20T14:10:00"));
    }

    @Test
    void saveQuestionsReturnsValidationDetails() throws Exception {
        given(coverLetterService.saveQuestions(
                "cl_questions",
                List.of(new SaveQuestionInput(" ", 99, " "))
        )).willThrow(new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                List.of(
                        new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                                "questions[0].question",
                                "질문을 입력해야 합니다."
                        ),
                        new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                                "questions[0].maxAnswerLength",
                                "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
                        ),
                        new com.daon.rewrite.global.response.ErrorResponse.ErrorDetail(
                                "questions[0].originalAnswer",
                                "답변을 입력해야 합니다."
                        )
                )
        ));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/questions", "cl_questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": [
                                    {
                                      "question": " ",
                                      "maxAnswerLength": 99,
                                      "originalAnswer": " "
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("questions[0].question"))
                .andExpect(jsonPath("$.error.details[1].field").value("questions[0].maxAnswerLength"))
                .andExpect(jsonPath("$.error.details[2].field").value("questions[0].originalAnswer"));
    }

    @Test
    void saveQuestionsReturnsNotFound() throws Exception {
        given(coverLetterService.saveQuestions(
                "cl_missing",
                List.of(new SaveQuestionInput("질문", 1000, "답변"))
        )).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/questions", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": [
                                    {
                                      "question": "질문",
                                      "maxAnswerLength": 1000,
                                      "originalAnswer": "답변"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void saveQuestionsReturnsCoverLetterNotDraft() throws Exception {
        given(coverLetterService.saveQuestions(
                "cl_reviewing",
                List.of(new SaveQuestionInput("질문", 1000, "답변"))
        )).willThrow(new BusinessException(ErrorCode.COVER_LETTER_NOT_DRAFT));

        mockMvc.perform(put("/cover-letters/{coverLetterId}/questions", "cl_reviewing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": [
                                    {
                                      "question": "질문",
                                      "maxAnswerLength": 1000,
                                      "originalAnswer": "답변"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COVER_LETTER_NOT_DRAFT"));
    }

    @Test
    void submitReturnsPendingReviewJob() throws Exception {
        Instant submittedAt = Instant.parse("2026-06-20T05:10:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_submit", "user_1", submittedAt.minusSeconds(60));
        coverLetter.startReview(submittedAt);
        LlmJob job = LlmJob.pendingReview("job_1", coverLetter.getId(), submittedAt, 2);
        given(coverLetterService.submit("cl_submit"))
                .willReturn(new SubmitCoverLetterResult(coverLetter, job));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/submit", "cl_submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetterId").value("cl_submit"))
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.jobId").value("job_1"))
                .andExpect(jsonPath("$.latestReviewVersionId").value(nullValue()));
    }

    @Test
    void submitReturnsLatestReviewVersionWhenAlreadyReviewed() throws Exception {
        CoverLetter coverLetter = CoverLetter.draft(
                "cl_reviewed",
                "user_1",
                Instant.parse("2026-06-20T05:00:00Z")
        );
        ReflectionTestUtils.setField(coverLetter, "status", CoverLetterStatus.REVIEWED);
        coverLetter.setLatestReviewVersionId("rv_1");
        given(coverLetterService.submit("cl_reviewed"))
                .willReturn(new SubmitCoverLetterResult(coverLetter, null));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/submit", "cl_reviewed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetterId").value("cl_reviewed"))
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.jobId").value(nullValue()))
                .andExpect(jsonPath("$.latestReviewVersionId").value("rv_1"));
    }

    @Test
    void submitReturnsValidationDetails() throws Exception {
        given(coverLetterService.submit("cl_incomplete")).willThrow(new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                List.of(
                        new ErrorResponse.ErrorDetail("preferences", "채용 우대사항을 입력해야 합니다."),
                        new ErrorResponse.ErrorDetail("questions", "질문과 답변을 1개 이상 입력해야 합니다.")
                )
        ));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/submit", "cl_incomplete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("preferences"))
                .andExpect(jsonPath("$.error.details[0].reason").value("채용 우대사항을 입력해야 합니다."))
                .andExpect(jsonPath("$.error.details[1].field").value("questions"));
    }

    @Test
    void submitReturnsNotFound() throws Exception {
        given(coverLetterService.submit("cl_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/submit", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void submitReturnsLlmJobAlreadyRunning() throws Exception {
        given(coverLetterService.submit("cl_busy"))
                .willThrow(new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/submit", "cl_busy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LLM_JOB_ALREADY_RUNNING"));
    }
}
