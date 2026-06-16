package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
