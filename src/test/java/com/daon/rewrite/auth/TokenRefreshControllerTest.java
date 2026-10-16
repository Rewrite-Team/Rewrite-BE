package com.daon.rewrite.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.controller.TokenRefreshController;
import com.daon.rewrite.auth.service.TokenRefreshService;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * standalone MockMvc에 컨트롤러와 공통 예외 처리만 등록해 갱신 실패의 오류 응답과 쿠키 처리를 검증한다.
 * 인증 실패는 쿠키를 지우고, 내부 오류에는 쿠키 변경 응답이 없는지 확인한다.
 */
class TokenRefreshControllerTest {

    @Test
    void unauthorizedFailureReturnsCommonErrorAndClearsCookies() throws Exception {
        TokenRefreshService tokenRefreshService = org.mockito.Mockito.mock(TokenRefreshService.class);
        when(tokenRefreshService.refresh("refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TokenRefreshController(tokenRefreshService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void internalFailureReturnsCommonErrorWithoutClearingCookies() throws Exception {
        TokenRefreshService tokenRefreshService = org.mockito.Mockito.mock(TokenRefreshService.class);
        when(tokenRefreshService.refresh("refresh-token"))
                .thenThrow(new IllegalStateException("storage failure"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TokenRefreshController(tokenRefreshService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
