package com.daon.rewrite.interview.controller;

import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.service.InterviewMessageItemResult;
import com.daon.rewrite.interview.service.InterviewMessageListResult;
import com.daon.rewrite.interview.service.InterviewMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewMessageController.class)
class InterviewMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewMessageService interviewMessageService;

    @Test
    void getInterviewMessagesReturnsUserAndAssistantResponse() throws Exception {
        Instant createdAt = Instant.parse("2026-07-12T01:00:00Z");
        given(interviewMessageService.findMyInterviewMessages("it_1"))
                .willReturn(new InterviewMessageListResult(
                        "it_1",
                        List.of(
                                new InterviewMessageItemResult(
                                        "im_1",
                                        InterviewMessageRole.USER,
                                        "저는 프로젝트에서 API 설계를 담당했습니다.",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        createdAt
                                ),
                                new InterviewMessageItemResult(
                                        "im_2",
                                        InterviewMessageRole.ASSISTANT,
                                        "역할은 명확하지만 성과 설명이 부족합니다.",
                                        "성과와 의사결정 근거를 보강해야 합니다.",
                                        List.of("담당 역할을 구체적으로 언급했습니다."),
                                        List.of("성과 지표를 추가하세요."),
                                        78,
                                        "가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
                                        createdAt.plusSeconds(60)
                                )
                        )
                ));

        mockMvc.perform(get("/interview-threads/{threadId}/messages", "it_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("it_1"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("im_1"))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].content").value("저는 프로젝트에서 API 설계를 담당했습니다."))
                .andExpect(jsonPath("$.items[0].feedback").value(nullValue()))
                .andExpect(jsonPath("$.items[0].score").value(nullValue()))
                .andExpect(jsonPath("$.items[0].followUpQuestion").value(nullValue()))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-07-12T10:00:00"))
                .andExpect(jsonPath("$.items[1].id").value("im_2"))
                .andExpect(jsonPath("$.items[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.items[1].feedback.summary")
                        .value("성과와 의사결정 근거를 보강해야 합니다."))
                .andExpect(jsonPath("$.items[1].feedback.strengths[0]")
                        .value("담당 역할을 구체적으로 언급했습니다."))
                .andExpect(jsonPath("$.items[1].feedback.improvements[0]")
                        .value("성과 지표를 추가하세요."))
                .andExpect(jsonPath("$.items[1].score").value(78))
                .andExpect(jsonPath("$.items[1].followUpQuestion")
                        .value("가장 중요하게 고려한 트레이드오프는 무엇이었나요?"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-07-12T10:01:00"));
    }

    @Test
    void getInterviewMessagesReturnsEmptyItems() throws Exception {
        given(interviewMessageService.findMyInterviewMessages("it_1"))
                .willReturn(new InterviewMessageListResult("it_1", List.of()));

        mockMvc.perform(get("/interview-threads/{threadId}/messages", "it_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("it_1"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getInterviewMessagesReturnsNotFound() throws Exception {
        given(interviewMessageService.findMyInterviewMessages("it_missing"))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/interview-threads/{threadId}/messages", "it_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
