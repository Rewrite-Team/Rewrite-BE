package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.client.KakaoClient;
import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.config.AuthProperties;
import com.daon.rewrite.auth.config.FrontendTarget;
import com.daon.rewrite.auth.service.OAuthStateService.StateIssue;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인 시작과 callback의 state 검증·사용자 조회·Rewrite 로그인 처리를 연결한다.
 * 외부 호출은 {@link KakaoClient}에, 사용자·토큰 저장은 {@link LoginPersistenceService}에 맡긴다.
 * 결과에 따른 Cookie·redirect 응답은 컨트롤러가 작성한다.
 */
@Slf4j
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class KakaoLoginService {

    private final OAuthStateService stateService;
    private final KakaoClient kakaoClient;
    private final LoginPersistenceService loginPersistenceService;
    private final AuthProperties properties;

    /**
     * 복귀할 프론트엔드 환경을 state에 결합하고 카카오 인가 URL을 만든다.
     * state는 URL query로 전달하고, 함께 반환한 nonce는 컨트롤러가 로그인 시작 브라우저의 Cookie에 담는다.
     */
    public AuthorizeResult authorize(FrontendTarget frontendTarget) {
        StateIssue issue = stateService.issue(frontendTarget);
        URI authorizeUri = UriComponentsBuilder.fromUriString(properties.kakao().authorizeUri())
                .queryParam("client_id", properties.kakao().clientId())
                .queryParam("redirect_uri", properties.kakao().redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", issue.state())
                .build()
                .encode()
                .toUri();
        return new AuthorizeResult(authorizeUri, issue.browserNonce());
    }

    /**
     * 성공·취소·실패 판단에 앞서 {@link OAuthStateService}가 state와 브라우저 nonce를 검증하고 일회성으로 소비한다.
     * 검증 전에는 운영 환경을 사용하고, 검증 후에는 state에 결합된 환경으로 결과를 돌려보낸다.
     * 성공하면 Rewrite 인증 토큰을 반환하며, 내부 처리 오류는 원인을 로그에 남긴 뒤 일반 로그인 실패로 변환한다.
     */
    public LoginResult callback(String code, String error, String state, String browserNonce) {
        FrontendTarget frontendTarget = FrontendTarget.PRODUCTION;
        try {
            var consumedTarget = stateService.consume(state, browserNonce);
            if (consumedTarget.isEmpty()) {
                return LoginResult.failed(frontendTarget);
            }
            frontendTarget = consumedTarget.orElseThrow();
            // state 검증을 통과한 access_denied 응답만 사용자 취소로 구분한다.
            if ("access_denied".equals(error) && isBlank(code)) {
                return LoginResult.canceled(frontendTarget);
            }
            if (!isBlank(error) || isBlank(code)) {
                return LoginResult.failed(frontendTarget);
            }
            // 카카오 사용자 조회를 마친 뒤 LoginPersistenceService가 사용자 변경과 토큰 저장의 트랜잭션을 담당한다.
            KakaoUser kakaoUser = kakaoClient.getUser(code);
            return LoginResult.success(loginPersistenceService.login(kakaoUser), frontendTarget);
        } catch (RuntimeException e) {
            log.warn("Kakao login callback failed", e);
            return LoginResult.failed(frontendTarget);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AuthorizeResult(
            URI authorizeUri,
            String browserNonce
    ) {
    }

    public record LoginResult(
            Status status,
            AuthTokenPair tokens,
            FrontendTarget frontendTarget
    ) {

        public static LoginResult success(AuthTokenPair tokens, FrontendTarget frontendTarget) {
            return new LoginResult(Status.SUCCESS, tokens, frontendTarget);
        }

        public static LoginResult canceled(FrontendTarget frontendTarget) {
            return new LoginResult(Status.CANCELED, null, frontendTarget);
        }

        public static LoginResult failed(FrontendTarget frontendTarget) {
            return new LoginResult(Status.FAILED, null, frontendTarget);
        }

        public enum Status {
            SUCCESS,
            CANCELED,
            FAILED
        }
    }
}
