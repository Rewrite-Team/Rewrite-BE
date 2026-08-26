package com.daon.rewrite.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.controller.TokenRefreshController;
import com.daon.rewrite.auth.service.TokenRefreshService;
import com.daon.rewrite.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TokenRefreshControllerTest {

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
