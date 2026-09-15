package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.daon.rewrite.auth.client.KakaoClient;
import com.daon.rewrite.auth.client.KakaoClientException;
import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.config.AuthProperties;
import com.daon.rewrite.auth.config.FrontendTarget;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.KakaoAuthorizeResult;
import com.daon.rewrite.auth.service.KakaoLoginResult;
import com.daon.rewrite.auth.service.KakaoLoginService;
import com.daon.rewrite.auth.service.KakaoLoginStatus;
import com.daon.rewrite.auth.service.LoginPersistenceService;
import com.daon.rewrite.auth.service.OAuthStateService;
import com.daon.rewrite.auth.service.OAuthStateIssue;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

    @Mock
    private OAuthStateService stateService;
    @Mock
    private KakaoClient kakaoClient;
    @Mock
    private LoginPersistenceService persistenceService;

    private KakaoLoginService service;

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
        service = new KakaoLoginService(stateService, kakaoClient, persistenceService, properties);
    }

    @Test
    void authorizeBindsFrontendTargetToOAuthState() {
        when(stateService.issue(FrontendTarget.LOCAL))
                .thenReturn(new OAuthStateIssue("state.local", "nonce"));

        KakaoAuthorizeResult result = service.authorize(FrontendTarget.LOCAL);

        assertThat(result.authorizeUri().getQuery()).contains("state=state.local");
        assertThat(result.browserNonce()).isEqualTo("nonce");
    }

    @Test
    void rejectsInvalidStateBeforeCallingKakao() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.empty());

        KakaoLoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.FAILED);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.PRODUCTION);
        verify(kakaoClient, never()).getUser("code");
    }

    @Test
    void classifiesOnlyValidAccessDeniedAsCanceled() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.LOCAL));

        KakaoLoginResult result = service.callback(null, "access_denied", "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.CANCELED);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.LOCAL);
    }

    @Test
    void rejectsCodeAndErrorTogether() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.PRODUCTION));

        KakaoLoginResult result = service.callback("code", "access_denied", "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.FAILED);
        verify(kakaoClient, never()).getUser("code");
    }

    @Test
    void returnsTokensAfterSuccessfulLogin() {
        KakaoUser kakaoUser = new KakaoUser("123", "사용자", null);
        AuthTokenPair tokens = new AuthTokenPair("access", "refresh");
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.LOCAL));
        when(kakaoClient.getUser("code")).thenReturn(kakaoUser);
        when(persistenceService.login(kakaoUser)).thenReturn(tokens);

        KakaoLoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.SUCCESS);
        assertThat(result.tokens()).isEqualTo(tokens);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.LOCAL);
    }

    @Test
    void hidesProviderFailureBehindGenericFailure() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.PRODUCTION));
        when(kakaoClient.getUser("code")).thenThrow(new KakaoClientException("provider detail"));

        KakaoLoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.FAILED);
        assertThat(result.tokens()).isNull();
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.PRODUCTION);
    }

    @Test
    void hidesStateStoreFailureBehindGenericFailure() {
        when(stateService.consume("state", "nonce"))
                .thenThrow(new IllegalStateException("database detail"));

        KakaoLoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(KakaoLoginStatus.FAILED);
        verify(kakaoClient, never()).getUser("code");
    }
}
