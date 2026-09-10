package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.dto.CsrfTokenResponse;
import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("auth-real")
@RequiredArgsConstructor
public class CsrfTokenController {

    private final CsrfTokenService csrfTokenService;

    /*
    1. 프론트엔드가 CSRF 토큰 요청
    2. CsrfTokenService.issue()
    3. 프론트엔드 메모리에 토큰 보관
    4. POST/PUT/PATCH/DELETE 요청의 X-CSRF-Token 헤더에 포함
    5. CsrfProtectionFilter
    6. CsrfTokenService.isValid()
    7. 유효하다면 Controller 진행
    8. 유효하지 않다면 403 응답
     */

    /*
    GET /auth/csrf-token은 인증 없이 호출할 수 있는 공개 발급 API 이다
    공격자도 토큰을 발급 받을 수 있다면 피해자 브라우저에서 CSRF 토큰 발급 후 POST요청하면 되는거 아닌가?
    하지만,
    - X-CSRF-Token은 일반적인 HTML form으로 붙일 수 없다
    - 따라서 공격자는 JavaScript fetch() 등을 사용해야 하지만, 현재 JavaScript는 https://공격자.com 에서 실행되고 API는 https://api.rewrite.com 이기에 cross-origin fetch 이다.
    - 현재 프로젝트에서는 허용 Origin을 https://rewrite.com 으로만 제한했기에 서버의 CORS 설정이 공격자.com을 거부
    따라서 CSRF 토큰이 공개 발급인데도 의미가 있다
     */
    @GetMapping("/auth/csrf-token")
    @RewriteApi(
            id = "API-003",
            summary = "CSRF 토큰 조회",
            tag = "인증",
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
