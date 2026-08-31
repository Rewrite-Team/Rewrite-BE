package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthCookieFactory;
import com.daon.rewrite.auth.service.LogoutService;
import com.daon.rewrite.global.response.SuccessResponse;
import com.daon.rewrite.global.openapi.RewriteApi;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!local & !test")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/logout")
    @ApiResponse(
            responseCode = "200",
            description = "멱등 로그아웃 완료",
            headers = @Header(name = "Set-Cookie", description = "access_token·refresh_token 만료", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SuccessResponse.class))
    )
    @RewriteApi(
            id = "API-006",
            summary = "로그아웃",
            tag = "인증",
            purpose = "유효한 refresh token을 폐기하고 인증 Cookie를 만료한다.",
            screens = "로그인",
            trigger = "사용자가 로그아웃을 확정할 때 진행 중인 refresh 흐름과 직렬화해 호출한다.",
            behavior = "인증 Cookie가 없거나 만료되어도 멱등하게 성공하며 access_token과 refresh_token Cookie를 모두 만료한다.",
            success = "프론트엔드 사용자 상태를 비우고 로그인 화면으로 이동한다.",
            authenticated = false,
            csrfProtected = true
    )
    public ResponseEntity<SuccessResponse> logout(
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        logoutService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.clearAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.clearRefreshToken().toString())
                .body(SuccessResponse.completed());
    }
}
