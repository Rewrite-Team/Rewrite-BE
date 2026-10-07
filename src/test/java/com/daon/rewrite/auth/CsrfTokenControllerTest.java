package com.daon.rewrite.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.controller.CsrfTokenController;
import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * standalone MockMvc에 컨트롤러와 공통 예외 처리만 등록해 토큰 발급 실패의 HTTP 오류 계약을 검증한다.
 * 인증 필터와 실제 서명 처리는 통합 테스트·서비스 테스트에서 다룬다.
 */
class CsrfTokenControllerTest {

    @Test
    void tokenIssuanceFailureReturnsInternalErrorContract() throws Exception {
        CsrfTokenService csrfTokenService = mock(CsrfTokenService.class);
        when(csrfTokenService.issue()).thenThrow(new IllegalStateException("signing failed"));
        var mockMvc = MockMvcBuilders
                .standaloneSetup(new CsrfTokenController(csrfTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/auth/csrf-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }
}
