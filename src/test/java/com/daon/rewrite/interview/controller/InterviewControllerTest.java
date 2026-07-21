package com.daon.rewrite.interview.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.AddInterviewQuestionResult;
import com.daon.rewrite.interview.service.CurrentInterviewResult;
import com.daon.rewrite.interview.service.InterviewQuestionItemResult;
import com.daon.rewrite.interview.service.InterviewQuestionListResult;
import com.daon.rewrite.interview.service.InterviewService;
import com.daon.rewrite.interview.service.StartInterviewResult;
import com.daon.rewrite.llmjob.entity.LlmJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    @Test
    void startInterviewReturnsQuestionGeneratingSessionAndPendingJob() throws Exception {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        LlmJob job = LlmJob.pendingInitialInterviewQuestionGeneration(
                "job_1", "cl_1", "rv_1", now.plusSeconds(60)
        );
        given(interviewService.startMyInterview("cl_1", "rv_1"))
                .willReturn(new StartInterviewResult(interviewSession, job));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/interviews", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceReviewVersionId": "rv_1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewSessionId").value("is_1"))
                .andExpect(jsonPath("$.jobId").value("job_1"))
                .andExpect(jsonPath("$.status").value(InterviewSessionStatus.QUESTION_GENERATING.name()));
    }

    @Test
    void startInterviewAcceptsEmptyBodyAndUsesLatestVersion() throws Exception {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        LlmJob job = LlmJob.pendingInitialInterviewQuestionGeneration(
                "job_1", "cl_1", "rv_1", now.plusSeconds(60)
        );
        given(interviewService.startMyInterview("cl_1", null))
                .willReturn(new StartInterviewResult(interviewSession, job));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/interviews", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewSessionId").value("is_1"));
    }

    @Test
    void startInterviewReturnsExistingActiveSessionWithoutJob() throws Exception {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        interviewSession.activate();
        given(interviewService.startMyInterview("cl_1", null))
                .willReturn(new StartInterviewResult(interviewSession, null));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/interviews", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewSessionId").value("is_1"))
                .andExpect(jsonPath("$.jobId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(InterviewSessionStatus.ACTIVE.name()));
    }

    @Test
    void startInterviewReturnsNotFound() throws Exception {
        given(interviewService.startMyInterview("cl_missing", null))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/interviews", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void startInterviewReturnsConflictWhenJobAlreadyRunning() throws Exception {
        given(interviewService.startMyInterview("cl_1", null))
                .willThrow(new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/interviews", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LLM_JOB_ALREADY_RUNNING"));
    }

    @Test
    void addInterviewQuestionReturnsActiveSessionAndPendingJob() throws Exception {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1", coverLetter, "rv_1", now.plusSeconds(60)
        );
        interviewSession.activate();
        LlmJob job = LlmJob.pendingAdditionalInterviewQuestionGeneration(
                "job_1", "cl_1", "rv_2", now.plusSeconds(120)
        );
        given(interviewService.addMyInterviewQuestion("is_1"))
                .willReturn(new AddInterviewQuestionResult(interviewSession, job));

        mockMvc.perform(post("/interviews/{interviewSessionId}/questions", "is_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewSessionId").value("is_1"))
                .andExpect(jsonPath("$.jobId").value("job_1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"));
    }

    @Test
    void addInterviewQuestionReturnsNotFoundAndConflictErrors() throws Exception {
        given(interviewService.addMyInterviewQuestion("is_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));
        given(interviewService.addMyInterviewQuestion("is_generating"))
                .willThrow(new BusinessException(ErrorCode.CONFLICT));
        given(interviewService.addMyInterviewQuestion("is_running"))
                .willThrow(new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING));

        mockMvc.perform(post("/interviews/{interviewSessionId}/questions", "is_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(post("/interviews/{interviewSessionId}/questions", "is_generating"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        mockMvc.perform(post("/interviews/{interviewSessionId}/questions", "is_running"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LLM_JOB_ALREADY_RUNNING"));
    }

    @Test
    void getCurrentInterviewReturnsNullWhenSessionDoesNotExist() throws Exception {
        given(interviewService.findMyCurrentInterview("cl_1"))
                .willReturn(new CurrentInterviewResult("cl_1", null));

        mockMvc.perform(get("/cover-letters/{coverLetterId}/interview", "cl_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetterId").value("cl_1"))
                .andExpect(jsonPath("$.interviewSession").value(nullValue()));
    }

    @Test
    void getCurrentInterviewReturnsEachSessionStatusAndSeoulCreatedAt() throws Exception {
        Instant createdAt = Instant.parse("2026-07-10T01:00:00Z");

        for (InterviewSessionStatus sessionStatus : InterviewSessionStatus.values()) {
            String coverLetterId = "cl_" + sessionStatus.name().toLowerCase();
            InterviewSession interviewSession = sessionWithStatus(
                    "is_" + sessionStatus.name().toLowerCase(),
                    coverLetterId,
                    sessionStatus,
                    createdAt
            );
            given(interviewService.findMyCurrentInterview(coverLetterId))
                    .willReturn(new CurrentInterviewResult(coverLetterId, interviewSession));

            mockMvc.perform(get("/cover-letters/{coverLetterId}/interview", coverLetterId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverLetterId").value(coverLetterId))
                    .andExpect(jsonPath("$.interviewSession.id").value(interviewSession.getId()))
                    .andExpect(jsonPath("$.interviewSession.initialSourceReviewVersionId").value("rv_1"))
                    .andExpect(jsonPath("$.interviewSession.status").value(sessionStatus.name()))
                    .andExpect(jsonPath("$.interviewSession.createdAt").value("2026-07-10T10:00:00"));
        }
    }

    @Test
    void getCurrentInterviewReturnsNotFound() throws Exception {
        given(interviewService.findMyCurrentInterview("cl_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/cover-letters/{coverLetterId}/interview", "cl_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getInterviewQuestionsReturnsLatestItemsAndNextCursor() throws Exception {
        given(interviewService.findMyInterviewQuestions("is_1", null, 10))
                .willReturn(new InterviewQuestionListResult(
                        List.of(
                                new InterviewQuestionItemResult(
                                        "iq_2",
                                        2,
                                        "성과를 만들기 위해 어떤 행동을 했는지 설명해 주세요.",
                                        "it_2"
                                ),
                                new InterviewQuestionItemResult(
                                        "iq_1",
                                        1,
                                        "프로젝트에서 맡은 역할을 설명해 주세요.",
                                        "it_1"
                                )
                        ),
                        "MQ"
                ));

        mockMvc.perform(get("/interviews/{interviewSessionId}/questions", "is_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewSessionId").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("iq_2"))
                .andExpect(jsonPath("$.items[0].sourceReviewVersionId").doesNotExist())
                .andExpect(jsonPath("$.items[0].order").value(2))
                .andExpect(jsonPath("$.items[0].type").doesNotExist())
                .andExpect(jsonPath("$.items[0].question").value("성과를 만들기 위해 어떤 행동을 했는지 설명해 주세요."))
                .andExpect(jsonPath("$.items[0].threadId").value("it_2"))
                .andExpect(jsonPath("$.items[1].id").value("iq_1"))
                .andExpect(jsonPath("$.items[1].threadId").value("it_1"))
                .andExpect(jsonPath("$.nextCursor").value("MQ"));
    }

    @Test
    void getInterviewQuestionsReturnsEmptyItems() throws Exception {
        given(interviewService.findMyInterviewQuestions("is_1", "NQ", 5))
                .willReturn(new InterviewQuestionListResult(List.of(), null));

        mockMvc.perform(get("/interviews/{interviewSessionId}/questions", "is_1")
                        .queryParam("cursor", "NQ")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void getInterviewQuestionsReturnsNotFound() throws Exception {
        given(interviewService.findMyInterviewQuestions("is_missing", null, 10))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/interviews/{interviewSessionId}/questions", "is_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getInterviewQuestionsReturnsValidationErrorForInvalidCursorOrSize() throws Exception {
        given(interviewService.findMyInterviewQuestions("is_1", "invalid", 10))
                .willThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));
        given(interviewService.findMyInterviewQuestions("is_1", null, 21))
                .willThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/interviews/{interviewSessionId}/questions", "is_1")
                        .queryParam("cursor", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/interviews/{interviewSessionId}/questions", "is_1")
                        .queryParam("size", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private InterviewSession sessionWithStatus(
            String interviewSessionId,
            String coverLetterId,
            InterviewSessionStatus status,
            Instant createdAt
    ) {
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", createdAt);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                interviewSessionId,
                coverLetter,
                "rv_1",
                createdAt
        );
        if (status == InterviewSessionStatus.ACTIVE) {
            interviewSession.activate();
        } else if (status == InterviewSessionStatus.FAILED) {
            interviewSession.fail();
        }
        return interviewSession;
    }
}
