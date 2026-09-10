package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.client.KakaoClient;
import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.config.AuthProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class KakaoLoginService {

    private final OAuthStateService stateService;
    private final KakaoClient kakaoClient;
    private final LoginPersistenceService loginPersistenceService;
    private final AuthProperties properties;

    public KakaoAuthorizeResult authorize() {
        OAuthStateIssue issue = stateService.issue();
        URI authorizeUri = UriComponentsBuilder.fromUriString(properties.kakao().authorizeUri())
                .queryParam("client_id", properties.kakao().clientId())
                .queryParam("redirect_uri", properties.kakao().redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", issue.state()) // CSRF 공격으로부터 카카오 로그인 요청을 보호하기 위해 사용하는 사용자의 로그인 요청에 대한 고유한 값
                .build()
                .encode()
                .toUri();
        return new KakaoAuthorizeResult(authorizeUri, issue.browserNonce());
    }

    public KakaoLoginResult callback(String code, String error, String state, String browserNonce) {
        try {
            if (!stateService.consume(state, browserNonce)) {
                // state 와 browserNonce 를 정상적으로 소비하지 못했다면 실패 결과를 반환
                return KakaoLoginResult.failed();
            }
            // 사용자가 로그인 취소
            if ("access_denied".equals(error) && isBlank(code)) {
                return KakaoLoginResult.canceled();
            }
            // 위의 로그인 취소 조건을 통과하지 못하는 요청에 대해 성공 callback 형태가 맞는지 확인
            if (!isBlank(error) || isBlank(code)) {
                return KakaoLoginResult.failed();
            }
            // 카카오에서 전달받은 authorization code를 사용해 카카오 사용자 정보를 가져옴
            KakaoUser kakaoUser = kakaoClient.getUser(code);
            // 조회한 카카오 사용자 정보로 Rewrite 서비스 로그인 처리
            // 기존 사용자 검색 - 없으면 사용자 생성 - Rewrite access token 생성 - Rewrite refresh token Tㅐㅇ성 - AuthTokenPair 반환
            return KakaoLoginResult.success(loginPersistenceService.login(kakaoUser));
        } catch (RuntimeException e) {
            // callback 처리중 발생하는 내부 오류를 컨트롤러까지 그대로 던지지 않고 일반 로그인 실패로 변환
            log.warn("Kakao login callback failed", e);
            return KakaoLoginResult.failed();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
