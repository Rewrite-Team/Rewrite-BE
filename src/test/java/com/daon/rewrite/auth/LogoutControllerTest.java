package com.daon.rewrite.auth;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.controller.LogoutController;
import com.daon.rewrite.auth.service.LogoutService;
import com.daon.rewrite.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * standalone MockMvc에 컨트롤러와 공통 예외 처리만 등록해 저장소 실패의 HTTP 응답을 검증한다.
 * 내부 오류가 나면 쿠키 삭제 응답을 내리지 않는지 확인하며, 인증·CSRF 필터 검증은 통합 테스트에서 다룬다.
 */
class LogoutControllerTest {

    @Test
    void storageFailureReturnsCommonErrorWithoutClearingCookies() throws Exception {
        LogoutService logoutService = mock(LogoutService.class);
        doThrow(new IllegalStateException("storage failure"))
                .when(logoutService).logout("refresh-token");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LogoutController(logoutService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
