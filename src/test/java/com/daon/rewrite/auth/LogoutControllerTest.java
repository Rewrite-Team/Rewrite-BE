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
