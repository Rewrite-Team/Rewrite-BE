package com.daon.rewrite.keywordanalysis.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.service.KeywordAnalysisService;
import com.daon.rewrite.keywordanalysis.service.StartKeywordAnalysisResult;
import com.daon.rewrite.llmjob.entity.LlmJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KeywordAnalysisController.class)
class KeywordAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeywordAnalysisService keywordAnalysisService;

    @Test
    void startKeywordAnalysisReturnsProcessingAnalysisAndPendingJob() throws Exception {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        KeywordAnalysis keywordAnalysis = KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        LlmJob job = LlmJob.pendingKeywordAnalysis("job_1", "cl_1", now.plusSeconds(60));
        given(keywordAnalysisService.startMyKeywordAnalysis("cl_1", "rv_1"))
                .willReturn(new StartKeywordAnalysisResult(keywordAnalysis, job));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/keyword-analysis", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceReviewVersionId": "rv_1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job_1"))
                .andExpect(jsonPath("$.keywordAnalysisId").value("ka_1"))
                .andExpect(jsonPath("$.coverLetterId").value("cl_1"))
                .andExpect(jsonPath("$.status").value(KeywordAnalysisStatus.PROCESSING.name()));
    }

    @Test
    void startKeywordAnalysisAcceptsEmptyBodyAndUsesLatestVersion() throws Exception {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        KeywordAnalysis keywordAnalysis = KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        );
        LlmJob job = LlmJob.pendingKeywordAnalysis("job_1", "cl_1", now.plusSeconds(60));
        given(keywordAnalysisService.startMyKeywordAnalysis("cl_1", null))
                .willReturn(new StartKeywordAnalysisResult(keywordAnalysis, job));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/keyword-analysis", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywordAnalysisId").value("ka_1"));
    }

    @Test
    void startKeywordAnalysisReturnsNotFound() throws Exception {
        given(keywordAnalysisService.startMyKeywordAnalysis("cl_missing", null))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/keyword-analysis", "cl_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void startKeywordAnalysisReturnsConflictWhenJobAlreadyRunning() throws Exception {
        given(keywordAnalysisService.startMyKeywordAnalysis("cl_1", null))
                .willThrow(new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING));

        mockMvc.perform(post("/cover-letters/{coverLetterId}/keyword-analysis", "cl_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LLM_JOB_ALREADY_RUNNING"));
    }
}
