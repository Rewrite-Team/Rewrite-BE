package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthProperties;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.KakaoAuthorizeResult;
import com.daon.rewrite.auth.service.KakaoLoginResult;
import com.daon.rewrite.auth.service.KakaoLoginService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@Slf4j
@Profile("!local & !test")
@RequestMapping("/auth/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    static final String OAUTH_NONCE_COOKIE = "oauth_login_nonce";

    private final KakaoLoginService loginService;
    private final AuthProperties properties;

    // 사용자가 카카오 로그인 버튼 누르면 호출
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        try {
            KakaoAuthorizeResult result = loginService.authorize();
            return ResponseEntity.status(HttpStatus.FOUND)  // 302 Found 지정. 브라우저는 302 응답을 받으면 Location 헤더 주소로 이동
                    .location(result.authorizeUri())
                    .header(HttpHeaders.SET_COOKIE, oauthNonceCookie(result.browserNonce(), 300).toString())    // OAuth 요청을 시작한 브라우저와 callback을 받은 브라우저가 같은지 검증하기 위한 nonce 쿠키를 설정
                    .build();   // 비어있는 body
        } catch (RuntimeException e) {
            log.warn("Kakao login authorization failed", e);
            return redirect(loginFailureUri("KAKAO_LOGIN_FAILED"), clearOauthNonceCookie());
        }
    }

    // 사용자가 로그인 완료 후 카카오의 브라우저 리다이렉트로 인한 호출
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,    // 카카오가 발급한 authorization code (사용자가 로그인 취소 시 code가 오지 않고 error가 옴)
            @RequestParam(required = false) String error,   // OAuth 인증이 실패하거나 사용자가 취소했을 때 전달됨
            @RequestParam(required = false) String state,   // 로그인 시작 시 서버가 발급한 OAuth state (required=false 라면 해당 값 누락 시 Spring MVC가 400 Bad Request 반환. 공통 로그인 실패 리다이렉트로 처리하기 위해 required=true 로 설정)
            @CookieValue(name = OAUTH_NONCE_COOKIE, required = false) String browserNonce
    ) {
        KakaoLoginResult result = loginService.callback(code, error, state, browserNonce);
        return switch (result.status()) {
            case SUCCESS -> success(result.tokens());
            case CANCELED -> redirect(loginFailureUri("KAKAO_LOGIN_CANCELED"), clearOauthNonceCookie());
            case FAILED -> redirect(loginFailureUri("KAKAO_LOGIN_FAILED"), clearOauthNonceCookie());
        };
    }

    private ResponseEntity<Void> success(AuthTokenPair tokens) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(properties.frontendSuccessUrl()))
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokens.refreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, clearOauthNonceCookie().toString())
                .build();
    }

    private ResponseEntity<Void> redirect(URI location, ResponseCookie cookie) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private URI loginFailureUri(String errorCode) {
        return UriComponentsBuilder.fromUriString(properties.frontendLoginUrl())
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUri();
    }

    private static ResponseCookie accessTokenCookie(String value) {
        return authCookie("access_token", value, "/", 1800);    // access_token 30분 유지
    }

    private static ResponseCookie refreshTokenCookie(String value) {
        return authCookie("refresh_token", value, "/auth", 1209600);       // refresh_token 14일 유지
    }

    private static ResponseCookie oauthNonceCookie(String value, long maxAge) {
        return authCookie(OAUTH_NONCE_COOKIE, value, "/auth/kakao", maxAge); // nonce 는 /auth/kakao 에만 필요
    }

    // 카카오 로그인 검증에 사용한 일회성 Nonce 쿠키를 삭제
    private static ResponseCookie clearOauthNonceCookie() {
        return oauthNonceCookie("", 0);
    }

    private static ResponseCookie authCookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
