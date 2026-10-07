package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.dto.CsrfTokenResponse;
import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 Cookie와 별도로 사용할 CSRF 토큰의 공개 발급 진입점.
 * 발급은 CsrfTokenService에, 상태 변경 요청의 헤더 검증은 CsrfProtectionFilter에 맡긴다.
 */
@RestController
@Profile("auth-real")
@RequiredArgsConstructor
public class CsrfTokenController {

    private final CsrfTokenService csrfTokenService;

    /**
     * 일반 HTML form으로 붙일 수 없는 {@code X-CSRF-Token} 헤더에 사용할 토큰을 반환한다.
     * 공개 발급 응답을 읽는 cross-origin 요청은 AuthSecurityConfig의 CORS 허용 목록을 따른다.
     */
    @GetMapping("/auth/csrf-token")
    @RewriteApi(
            id = "API-003",
            operationId = "getCsrfToken",
            summary = "CSRF 토큰 조회",
            tag = "Auth",
            purpose = "상태 변경 요청의 X-CSRF-Token 헤더에 사용할 토큰을 인증 없이 발급한다.",
            screens = {"공통", "로그인"},
            trigger = "앱 초기화 또는 CSRF_TOKEN_INVALID 복구 시 호출한다.",
            behavior = "토큰은 브라우저 메모리에 보관하고 POST·PUT·PATCH·DELETE 요청 헤더에 사용한다.",
            success = "발급 토큰을 메모리에 교체하고 대기 중인 상태 변경 요청을 진행한다.",
            authenticated = false
    )
    public CsrfTokenResponse issue() {
        return new CsrfTokenResponse(csrfTokenService.issue());
    }
}
