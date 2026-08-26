package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.entity.User;
import com.daon.rewrite.auth.repository.OAuthLoginStateRepository;
import com.daon.rewrite.auth.repository.RefreshTokenRepository;
import com.daon.rewrite.auth.repository.UserRepository;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.AuthTokenService;
import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.auth.service.KakaoAuthorizeResult;
import com.daon.rewrite.auth.service.KakaoLoginResult;
import com.daon.rewrite.auth.service.KakaoLoginService;
import com.daon.rewrite.auth.service.LoginPersistenceService;
import com.daon.rewrite.auth.service.OAuthStateIssue;
import com.daon.rewrite.auth.service.OAuthStateService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("auth-test")
@Import(AuthIntegrationTest.CsrfProbeController.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OAuthLoginStateRepository oauthLoginStateRepository;
    @Autowired
    private OAuthStateService oauthStateService;
    @Autowired
    private LoginPersistenceService loginPersistenceService;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private CsrfTokenService csrfTokenService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private SecretKey authSecretKey;

    @MockitoBean
    private KakaoLoginService kakaoLoginService;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        oauthLoginStateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authorizeRedirectsWithBrowserNonceCookie() throws Exception {
        when(kakaoLoginService.authorize()).thenReturn(new KakaoAuthorizeResult(
                URI.create("https://kauth.kakao.com/oauth/authorize?state=state"),
                "browser-nonce"
        ));

        var result = mockMvc.perform(get("/auth/kakao/authorize")
                        .cookie(new Cookie("access_token", "invalid.jwt.token")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://kauth.kakao.com/oauth/authorize?state=state"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("oauth_login_nonce=browser-nonce;")
                .contains("Path=/auth/kakao", "Max-Age=300", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void successfulCallbackSetsAuthCookiesAndClearsNonce() throws Exception {
        when(kakaoLoginService.callback(eq("code"), eq(null), eq("state"), eq("browser-nonce")))
                .thenReturn(KakaoLoginResult.success(new AuthTokenPair("access", "refresh")));

        var result = mockMvc.perform(get("/auth/kakao/callback")
                .queryParam("code", "code")
                .queryParam("state", "state")
                .cookie(
                        new Cookie("oauth_login_nonce", "browser-nonce"),
                        new Cookie("access_token", "invalid.jwt.token")
                ))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://rewrite.example.com"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> hasCookieAttributes(
                        value, "access_token=access;", "Path=/", "Max-Age=1800"))
                .anyMatch(value -> hasCookieAttributes(
                        value, "refresh_token=refresh;", "Path=/auth", "Max-Age=1209600"))
                .anyMatch(value -> hasCookieAttributes(
                        value, "oauth_login_nonce=;", "Path=/auth/kakao", "Max-Age=0"));
    }

    @Test
    void failedCallbackDoesNotSetAuthCookies() throws Exception {
        when(kakaoLoginService.callback(any(), any(), any(), any()))
                .thenReturn(KakaoLoginResult.failed());

        var result = mockMvc.perform(get("/auth/kakao/callback")
                        .queryParam("state", "invalid"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED"
                ))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(1)
                .allMatch(value -> value.startsWith("oauth_login_nonce="));
    }

    @Test
    void oauthStateCanBeConsumedOnlyOnceByIssuingBrowser() {
        OAuthStateIssue issue = oauthStateService.issue();

        assertThat(oauthStateService.consume(issue.state(), "other-browser")).isFalse();
        assertThat(oauthStateService.consume(issue.state(), issue.browserNonce())).isTrue();
        assertThat(oauthStateService.consume(issue.state(), issue.browserNonce())).isFalse();
    }

    @Test
    void concurrentCallbacksConsumeOAuthStateExactlyOnce() throws Exception {
        OAuthStateIssue issue = oauthStateService.issue();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return oauthStateService.consume(issue.state(), issue.browserNonce());
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return oauthStateService.consume(issue.state(), issue.browserNonce());
            });

            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void repeatedKakaoLoginKeepsUserIdentityAndUpdatesProfile() {
        AuthTokenPair first = loginPersistenceService.login(
                new KakaoUser("kakao-1", "첫 이름", "https://image/first")
        );
        User firstUser = userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1")
                .orElseThrow();

        AuthTokenPair second = loginPersistenceService.login(
                new KakaoUser("kakao-1", "새 이름", null)
        );
        User updated = userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1")
                .orElseThrow();

        assertThat(first.accessToken()).isNotBlank();
        assertThat(second.refreshToken()).isNotBlank();
        assertThat(updated.getId()).isEqualTo(firstUser.getId());
        assertThat(updated.getCreatedAt()).isEqualTo(firstUser.getCreatedAt());
        assertThat(updated.getNickname()).isEqualTo("새 이름");
        assertThat(updated.getProfileImageUrl()).isNull();
        assertThat(userRepository.count()).isOne();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void userMeRequiresValidAccessCookie() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.details").isArray());

        mockMvc.perform(get("/user/me")
                        .cookie(new Cookie("access_token", "invalid.jwt.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void userMeRejectsTokensWithInvalidSecurityClaims() throws Exception {
        Instant now = Instant.now();

        for (String token : List.of(
                encodeToken("other-issuer", List.of("rewrite-web"), "access", now, now.plusSeconds(300)),
                encodeToken("rewrite", List.of("other-audience"), "access", now, now.plusSeconds(300)),
                encodeToken("rewrite", List.of("rewrite-web"), "refresh", now, now.plusSeconds(300)),
                encodeToken("rewrite", List.of("rewrite-web"), "access",
                        now.minusSeconds(600), now.minusSeconds(300))
        )) {
            mockMvc.perform(get("/user/me")
                            .cookie(new Cookie("access_token", token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void csrfTokenCanBeIssuedWithoutAuthentication() throws Exception {
        Instant now = Instant.now();
        String expiredAccessToken = encodeToken(
                "rewrite",
                List.of("rewrite-web"),
                "access",
                now.minusSeconds(600),
                now.minusSeconds(300)
        );

        var result = mockMvc.perform(get("/auth/csrf-token")
                        .cookie(new Cookie("access_token", expiredAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isString())
                .andReturn();

        String token = result.getResponse().getContentAsString()
                .replaceFirst("^\\{\"csrfToken\":\"", "")
                .replaceFirst("\"}$", "");
        assertThat(csrfTokenService.isValid(token)).isTrue();
    }

    @Test
    void stateChangingMethodsRequireValidCsrfToken() throws Exception {
        User user = userRepository.save(User.create(
                "user_csrf",
                AuthProvider.KAKAO,
                "kakao-csrf",
                "보안 사용자",
                null,
                Instant.now()
        ));
        String accessToken = authTokenService.issueAccessToken(user.getId());

        for (HttpMethod method : List.of(
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE
        )) {
            mockMvc.perform(request(method, "/csrf-probe")
                            .cookie(new Cookie("access_token", accessToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"))
                    .andExpect(jsonPath("$.error.details").isArray());
        }

        String expiredCsrfToken = new CsrfTokenService(
                authSecretKey,
                Clock.fixed(Instant.now().minusSeconds(1801), ZoneOffset.UTC)
        ).issue();
        for (String invalidToken : List.of("invalid", expiredCsrfToken)) {
            mockMvc.perform(request(HttpMethod.POST, "/csrf-probe")
                            .cookie(new Cookie("access_token", accessToken))
                            .header("X-CSRF-Token", invalidToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        }

        for (HttpMethod method : List.of(
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE
        )) {
            mockMvc.perform(request(method, "/csrf-probe")
                            .cookie(new Cookie("access_token", accessToken))
                            .header("X-CSRF-Token", csrfTokenService.issue()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void unauthenticatedStateChangingRequestReturnsUnauthorizedBeforeCsrfError() throws Exception {
        Instant now = Instant.now();
        String expiredAccessToken = encodeToken(
                "rewrite",
                List.of("rewrite-web"),
                "access",
                now.minusSeconds(600),
                now.minusSeconds(300)
        );
        for (Cookie accessCookie : List.of(
                new Cookie("unrelated", "value"),
                new Cookie("access_token", "invalid.jwt.token"),
                new Cookie("access_token", expiredAccessToken)
        )) {
            mockMvc.perform(request(HttpMethod.POST, "/cover-letters")
                            .cookie(accessCookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void userMeReturnsLatestStoredProfile() throws Exception {
        Instant createdAt = Instant.parse("2026-07-28T01:02:03Z");
        User user = userRepository.save(User.create(
                "user_1",
                AuthProvider.KAKAO,
                "kakao-1",
                "홍길동",
                null,
                createdAt
        ));
        String accessToken = authTokenService.issueAccessToken(user.getId());
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo("user_1");
        assertThat(jwt.getClaimAsString("purpose")).isEqualTo("access");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(1800));

        mockMvc.perform(get("/user/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user_1"))
                .andExpect(jsonPath("$.nickname").value("홍길동"))
                .andExpect(jsonPath("$.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-28T10:02:03"));
    }

    @Test
    void corsAllowsConfiguredFrontendWithCredentials() throws Exception {
        mockMvc.perform(options("/user/me")
                        .header(HttpHeaders.ORIGIN, "https://rewrite.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://rewrite.example.com"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    private static boolean hasCookieAttributes(String value, String prefix, String path, String maxAge) {
        return value.startsWith(prefix)
                && value.contains(path)
                && value.contains(maxAge)
                && value.contains("Secure")
                && value.contains("HttpOnly")
                && value.contains("SameSite=Lax");
    }

    private String encodeToken(
            String issuer,
            List<String> audience,
            String purpose,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .subject("user_1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("purpose", purpose)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @RestController
    static class CsrfProbeController {

        @RequestMapping(
                path = "/csrf-probe",
                method = {
                        RequestMethod.POST,
                        RequestMethod.PUT,
                        RequestMethod.PATCH,
                        RequestMethod.DELETE
                }
        )
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void changeState() {
        }
    }
}
