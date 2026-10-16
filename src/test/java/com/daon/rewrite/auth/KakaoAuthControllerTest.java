package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.config.AuthProperties;
import com.daon.rewrite.auth.config.FrontendTarget;
import com.daon.rewrite.auth.controller.KakaoAuthController;
import com.daon.rewrite.auth.service.KakaoLoginService;
import com.daon.rewrite.auth.service.KakaoLoginService.LoginResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 공개 redirect 오류 코드와 provider 입력의 경계를 standalone MockMvc에서 검증한다. */
@ExtendWith(MockitoExtension.class)
class KakaoAuthControllerTest {

    @Mock
    private KakaoLoginService loginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "rewrite",
                "rewrite-web",
                "unused",
                "https://rewrite.example.com",
                "https://rewrite.example.com/writing",
                "https://rewrite.example.com/login",
                "http://localhost:3000",
                "http://localhost:3000/writing",
                "http://localhost:3000/login",
                new AuthProperties.Kakao(
                        "client",
                        "secret",
                        "https://api.example.com/auth/kakao/callback",
                        "https://kauth.kakao.com/oauth/authorize",
                        "https://kauth.kakao.com/oauth/token",
                        "https://kapi.kakao.com/v2/user/me"
                )
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new KakaoAuthController(loginService, properties)).build();
    }

    @Test
    void invalidFrontendTargetUsesProductionFailureRedirect() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/kakao/authorize")
                        .queryParam("target", "https://untrusted.example.com"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED"))
                .andExpect(content().string(""))
                .andReturn();

        assertNonceCleared(result);
        verifyNoInteractions(loginService);
    }

    @Test
    void authorizeFailureKeepsSelectedFrontendAndClearsNonce() throws Exception {
        when(loginService.authorize(FrontendTarget.LOCAL)).thenThrow(new IllegalStateException("failure"));

        MvcResult result = mockMvc.perform(get("/auth/kakao/authorize").queryParam("target", "local"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/login?error=KAKAO_LOGIN_FAILED"))
                .andExpect(content().string(""))
                .andReturn();

        assertNonceCleared(result);
    }

    @Test
    void canceledCallbackReturnsPublicCanceledCodeAndClearsNonce() throws Exception {
        when(loginService.callback(null, "access_denied", "state", "nonce"))
                .thenReturn(LoginResult.canceled(FrontendTarget.LOCAL));

        MvcResult result = mockMvc.perform(get("/auth/kakao/callback")
                        .queryParam("error", "access_denied")
                        .queryParam("state", "state")
                        .cookie(new Cookie("oauth_login_nonce", "nonce")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/login?error=KAKAO_LOGIN_CANCELED"))
                .andExpect(content().string(""))
                .andReturn();

        assertNonceCleared(result);
    }

    @Test
    void arbitraryProviderErrorRemainsStringInputAndReturnsPublicFailedCode() throws Exception {
        when(loginService.callback(null, "new_provider_error", null, null))
                .thenReturn(LoginResult.failed(FrontendTarget.PRODUCTION));

        MvcResult result = mockMvc.perform(get("/auth/kakao/callback")
                        .queryParam("error", "new_provider_error"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED"))
                .andExpect(content().string(""))
                .andReturn();

        assertNonceCleared(result);
        verify(loginService).callback(null, "new_provider_error", null, null);
    }

    private void assertNonceCleared(MvcResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie)
                        .startsWith("oauth_login_nonce=;")
                        .contains("Path=/auth/kakao", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax"));
    }
}
