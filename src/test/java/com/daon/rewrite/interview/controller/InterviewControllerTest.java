package com.daon.rewrite.interview.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
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
        LlmJob job = LlmJob.pendingInterviewQuestionGeneration("job_1", "cl_1", now.plusSeconds(60));
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
        LlmJob job = LlmJob.pendingInterviewQuestionGeneration("job_1", "cl_1", now.plusSeconds(60));
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
}
