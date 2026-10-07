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
import com.daon.rewrite.auth.service.KakaoLoginService.AuthorizeResult;
import com.daon.rewrite.auth.service.KakaoLoginService.LoginResult;
import com.daon.rewrite.auth.service.KakaoLoginService;
import com.daon.rewrite.auth.service.KakaoLoginService.LoginResult.Status;
import com.daon.rewrite.auth.service.LoginPersistenceService;
import com.daon.rewrite.auth.service.OAuthStateService;
import com.daon.rewrite.auth.service.OAuthStateService.StateIssue;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OAuth state·Kakao 조회·로그인 저장을 mock으로 제어해 callback의 분류와 실패 변환을 검증한다.
 * state 검증에 실패하면 외부 조회를 시작하지 않는 경계와, 검증된 target이 결과에 전달되는지 확인한다.
 */
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
                .thenReturn(new StateIssue("state.local", "nonce"));

        AuthorizeResult result = service.authorize(FrontendTarget.LOCAL);

        assertThat(result.authorizeUri().getQuery()).contains("state=state.local");
        assertThat(result.browserNonce()).isEqualTo("nonce");
    }

    @Test
    void rejectsInvalidStateBeforeCallingKakao() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.empty());

        LoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.PRODUCTION);
        verify(kakaoClient, never()).getUser("code");
    }

    @Test
    void classifiesOnlyValidAccessDeniedAsCanceled() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.LOCAL));

        LoginResult result = service.callback(null, "access_denied", "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.CANCELED);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.LOCAL);
    }

    @Test
    void rejectsCodeAndErrorTogether() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.PRODUCTION));

        LoginResult result = service.callback("code", "access_denied", "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        verify(kakaoClient, never()).getUser("code");
    }

    @Test
    void returnsTokensAfterSuccessfulLogin() {
        KakaoUser kakaoUser = new KakaoUser("123", "사용자", null);
        AuthTokenPair tokens = new AuthTokenPair("access", "refresh");
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.LOCAL));
        when(kakaoClient.getUser("code")).thenReturn(kakaoUser);
        when(persistenceService.login(kakaoUser)).thenReturn(tokens);

        LoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.tokens()).isEqualTo(tokens);
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.LOCAL);
    }

    @Test
    void hidesProviderFailureBehindGenericFailure() {
        when(stateService.consume("state", "nonce")).thenReturn(Optional.of(FrontendTarget.PRODUCTION));
        when(kakaoClient.getUser("code")).thenThrow(new KakaoClientException("provider detail"));

        LoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.tokens()).isNull();
        assertThat(result.frontendTarget()).isEqualTo(FrontendTarget.PRODUCTION);
    }

    @Test
    void hidesStateStoreFailureBehindGenericFailure() {
        when(stateService.consume("state", "nonce"))
                .thenThrow(new IllegalStateException("database detail"));

        LoginResult result = service.callback("code", null, "state", "nonce");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        verify(kakaoClient, never()).getUser("code");
    }
}
