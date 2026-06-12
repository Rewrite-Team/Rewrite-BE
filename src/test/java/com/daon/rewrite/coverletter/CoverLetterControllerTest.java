package com.daon.rewrite.coverletter;

import com.daon.rewrite.coverletter.dto.CoverLetterCreateRequest;
import com.daon.rewrite.coverletter.dto.CoverLetterCreateResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDeleteResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterSummaryResponse;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoverLetterController.class)
@Import(GlobalExceptionHandler.class)
@Disabled("CoverLetter 기본 CRUD 구현 PR에서 활성화")
class CoverLetterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoverLetterService coverLetterService;

    @Test
    void createCoverLetterReturnsCreatedDraftCoverLetter() throws Exception {
        LocalDateTime createdAt = LocalDateTime.parse("2026-06-20T14:00:00");
        when(coverLetterService.create(any(CoverLetterCreateRequest.class)))
                .thenReturn(new CoverLetterCreateResponse(
                        "cl_001",
                        CoverLetterStatus.DRAFT,
                        createdAt
                ));

        mockMvc.perform(post("/cover-letters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "백엔드 자기소개서",
                                  "companyName": "Rewrite",
                                  "positionTitle": "백엔드 개발자",
                                  "jobPostingUrl": "https://example.com/jobs/1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cl_001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value("2026-06-20T14:00:00"));

        verify(coverLetterService).create(any(CoverLetterCreateRequest.class));
    }

    @Test
    void createCoverLetterReturnsValidationErrorWhenRequiredFieldsAreBlank() throws Exception {
        mockMvc.perform(post("/cover-letters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "companyName": "",
                                  "positionTitle": "",
                                  "jobPostingUrl": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());

        verifyNoInteractions(coverLetterService);
    }

    @Test
    void createCoverLetterReturnsValidationErrorWhenFieldsExceedMaxLength() throws Exception {
        mockMvc.perform(post("/cover-letters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "123456789012345678901234567890123456789012345678901",
                                  "companyName": "1234567890123456789012345678901",
                                  "positionTitle": "1234567890123456789012345678901",
                                  "jobPostingUrl": "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());

        verifyNoInteractions(coverLetterService);
    }

    @Test
    void findCoverLettersReturnsListResponse() throws Exception {
        LocalDateTime createdAt = LocalDateTime.parse("2026-06-20T14:00:00");
        when(coverLetterService.findAll())
                .thenReturn(new CoverLetterListResponse(
                        List.of(new CoverLetterSummaryResponse(
                                "cl_001",
                                "백엔드 자기소개서",
                                "Rewrite",
                                "백엔드 개발자",
                                CoverLetterStatus.DRAFT,
                                createdAt
                        )),
                        1,
                        9,
                        1,
                        1
                ));

        mockMvc.perform(get("/cover-letters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("cl_001"))
                .andExpect(jsonPath("$.items[0].title").value("백엔드 자기소개서"))
                .andExpect(jsonPath("$.items[0].companyName").value("Rewrite"))
                .andExpect(jsonPath("$.items[0].positionTitle").value("백엔드 개발자"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-06-20T14:00:00"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(9))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findCoverLetterReturnsDetailResponse() throws Exception {
        LocalDateTime createdAt = LocalDateTime.parse("2026-06-20T14:00:00");
        LocalDateTime updatedAt = LocalDateTime.parse("2026-06-20T14:05:00");
        when(coverLetterService.findById("cl_001"))
                .thenReturn(new CoverLetterDetailResponse(
                        "cl_001",
                        "백엔드 자기소개서",
                        "Rewrite",
                        "백엔드 개발자",
                        "https://example.com/jobs/1",
                        CoverLetterStatus.DRAFT,
                        createdAt,
                        updatedAt
                ));

        mockMvc.perform(get("/cover-letters/{coverLetterId}", "cl_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cl_001"))
                .andExpect(jsonPath("$.title").value("백엔드 자기소개서"))
                .andExpect(jsonPath("$.companyName").value("Rewrite"))
                .andExpect(jsonPath("$.positionTitle").value("백엔드 개발자"))
                .andExpect(jsonPath("$.jobPostingUrl").value("https://example.com/jobs/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value("2026-06-20T14:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-20T14:05:00"));
    }

    @Test
    void findCoverLetterReturnsNotFoundWhenCoverLetterDoesNotExist() throws Exception {
        when(coverLetterService.findById("missing"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/cover-letters/{coverLetterId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("리소스를 찾을 수 없습니다."));
    }

    @Test
    void deleteCoverLetterReturnsDeletedAt() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.parse("2026-06-20T15:00:00");
        when(coverLetterService.delete("cl_001"))
                .thenReturn(new CoverLetterDeleteResponse(true, deletedAt));

        mockMvc.perform(delete("/cover-letters/{coverLetterId}", "cl_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.deletedAt").value("2026-06-20T15:00:00"));
    }

    @Test
    void deleteCoverLetterReturnsNotFoundWhenCoverLetterDoesNotExist() throws Exception {
        when(coverLetterService.delete("missing"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(delete("/cover-letters/{coverLetterId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("리소스를 찾을 수 없습니다."));
    }
}
