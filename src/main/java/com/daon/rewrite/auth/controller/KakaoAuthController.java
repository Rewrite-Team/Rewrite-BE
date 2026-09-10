package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthProperties;
import com.daon.rewrite.auth.config.AuthCookieFactory;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.KakaoAuthorizeResult;
import com.daon.rewrite.auth.service.KakaoLoginResult;
import com.daon.rewrite.auth.service.KakaoLoginService;
import com.daon.rewrite.global.openapi.ApiRedirectError;
import com.daon.rewrite.global.openapi.RewriteApi;
import java.net.URI;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Slf4j
@Profile("auth-real")
@RequestMapping("/auth/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    static final String OAUTH_NONCE_COOKIE = "oauth_login_nonce";

    private final KakaoLoginService loginService;
    private final AuthProperties properties;

    // 사용자가 카카오 로그인 버튼 누르면 호출
    @GetMapping("/authorize")
    @ApiResponse(
            responseCode = "302",
            description = "카카오 인가 화면 또는 로그인 실패 화면으로 redirect",
            headers = {
                    @Header(name = "Location", description = "카카오 인가 URL 또는 error query가 포함된 로그인 URL", schema = @Schema(type = "string", format = "uri")),
                    @Header(name = "Set-Cookie", description = "oauth_login_nonce 발급 또는 만료", schema = @Schema(type = "string"))
            }
    )
    @RewriteApi(
            id = "API-001",
            summary = "카카오 로그인 시작",
            tag = "인증",
            purpose = "카카오 OAuth 인가를 시작하고 요청 브라우저 검증용 nonce Cookie를 발급한다.",
            screens = "로그인",
            trigger = "사용자가 카카오 로그인 버튼을 누를 때 브라우저를 이 endpoint로 이동시킨다.",
            behavior = "JSON 호출이 아니라 302 redirect 흐름이다. oauth_login_nonce Cookie는 HttpOnly로 발급하며 프론트엔드가 읽지 않는다.",
            success = "302 Location의 카카오 인가 화면으로 이동하고 로그인 버튼 중복 클릭을 막는다.",
            successStatus = 302,
            authenticated = false,
            includeInternalError = false,
            redirectErrors = @ApiRedirectError(
                    code = "KAKAO_LOGIN_FAILED",
                    condition = "카카오 인가 URL 생성 등 로그인 시작 처리 실패",
                    action = "로그인 화면에서 일반 실패 안내를 표시하고 버튼을 다시 활성화하며 자동 재시도하지 않는다."
            )
    )
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
    @ApiResponse(
            responseCode = "302",
            description = "로그인 성공·취소·실패 결과에 따른 프론트엔드 redirect",
            headers = {
                    @Header(name = "Location", description = "앱 성공 URL 또는 error query가 포함된 로그인 URL", schema = @Schema(type = "string", format = "uri")),
                    @Header(name = "Set-Cookie", description = "성공 시 인증 Cookie 발급, 모든 결과에서 oauth_login_nonce 만료", schema = @Schema(type = "string"))
            }
    )
    @RewriteApi(
            id = "API-002",
            summary = "카카오 OAuth callback",
            tag = "인증",
            purpose = "카카오 인가 결과를 검증하고 성공 시 Rewrite 인증 Cookie를 발급한다.",
            screens = "로그인",
            trigger = "카카오가 로그인·동의 처리 후 브라우저를 callback으로 돌려보낼 때 호출된다.",
            behavior = "프론트엔드가 직접 호출하지 않는 브라우저 redirect endpoint다. state와 nonce를 일회성으로 검증하고 모든 결과에서 nonce Cookie를 만료한다.",
            success = "302로 앱에 이동하며 access_token과 refresh_token HttpOnly Cookie를 발급한다.",
            successStatus = 302,
            authenticated = false,
            includeInternalError = false,
            redirectErrors = {
                    @ApiRedirectError(
                            code = "KAKAO_LOGIN_CANCELED",
                            condition = "사용자가 카카오 로그인 또는 동의를 취소함",
                            action = "취소 안내를 표시하고 로그인 버튼을 다시 활성화한다."
                    ),
                    @ApiRedirectError(
                            code = "KAKAO_LOGIN_FAILED",
                            condition = "state·nonce 검증, code 교환, 사용자 조회 또는 저장 실패",
                            action = "일반 로그인 실패 안내를 표시하고 내부 원인은 노출하지 않으며 자동 재시도하지 않는다."
                    )
            }
    )
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
                .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.accessToken(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.refreshToken(tokens.refreshToken()).toString())
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

    private static ResponseCookie oauthNonceCookie(String value, long maxAge) {
        return AuthCookieFactory.cookie(OAUTH_NONCE_COOKIE, value, "/auth/kakao", maxAge); // nonce 는 /auth/kakao 에만 필요
    }

    // 카카오 로그인 검증에 사용한 일회성 Nonce 쿠키를 삭제
    private static ResponseCookie clearOauthNonceCookie() {
        return oauthNonceCookie("", 0);
    }

}
