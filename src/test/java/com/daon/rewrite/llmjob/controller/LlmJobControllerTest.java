package com.daon.rewrite.llmjob.controller;

import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.service.LlmJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmJobController.class)
class LlmJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmJobService llmJobService;

    @Test
    void findJobReturnsPendingJob() throws Exception {
        LlmJob job = LlmJob.pendingReview(
                "job_1",
                "cl_1",
                Instant.parse("2026-06-20T05:10:00Z"),
                3
        );
        given(llmJobService.findMyJob("job_1")).willReturn(job);

        mockMvc.perform(get("/llm-jobs/{jobId}", "job_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job_1"))
                .andExpect(jsonPath("$.type").value("COVER_LETTER_REVIEW"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetType").value("COVER_LETTER"))
                .andExpect(jsonPath("$.targetId").value("cl_1"))
                .andExpect(jsonPath("$.progress.current").value(0))
                .andExpect(jsonPath("$.progress.total").value(3))
                .andExpect(jsonPath("$.progress.message").doesNotExist())
                .andExpect(jsonPath("$.attempt").value(1))
                .andExpect(jsonPath("$.maxAttempts").value(2))
                .andExpect(jsonPath("$.partialResult").doesNotExist())
                .andExpect(jsonPath("$.resultRef").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-06-20T14:10:00"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void findJobReturnsCompletedJobWithResultRef() throws Exception {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        LlmJob job = LlmJob.pendingReview("job_done", "cl_1", now, 3);
        job.markCompleted(
                3,
                "첨삭이 완료되었습니다.",
                LlmJobResultRefType.REVIEW_VERSION,
                "rv_1",
                now.plusSeconds(60)
        );
        given(llmJobService.findMyJob("job_done")).willReturn(job);

        mockMvc.perform(get("/llm-jobs/{jobId}", "job_done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.current").value(3))
                .andExpect(jsonPath("$.progress.message").value("첨삭이 완료되었습니다."))
                .andExpect(jsonPath("$.resultRef.type").value("REVIEW_VERSION"))
                .andExpect(jsonPath("$.resultRef.id").value("rv_1"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.completedAt").value("2026-06-20T14:11:00"));
    }

    @Test
    void findJobReturnsFailedJobWithError() throws Exception {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        LlmJob job = LlmJob.pendingReview("job_failed", "cl_1", now, 3);
        job.markFailed(
                0,
                "LLM 첨삭에 실패했습니다.",
                "LLM_PROVIDER_ERROR",
                "LLM 응답 생성에 실패했습니다.",
                now.plusSeconds(60)
        );
        given(llmJobService.findMyJob("job_failed")).willReturn(job);

        mockMvc.perform(get("/llm-jobs/{jobId}", "job_failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.resultRef").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("LLM_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.error.message").value("LLM 응답 생성에 실패했습니다."))
                .andExpect(jsonPath("$.completedAt").value("2026-06-20T14:11:00"));
    }

    @Test
    void findJobReturnsNotFound() throws Exception {
        given(llmJobService.findMyJob("job_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/llm-jobs/{jobId}", "job_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
