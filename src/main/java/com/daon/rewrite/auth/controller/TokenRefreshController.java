package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthCookieFactory;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.TokenRefreshService;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.response.SuccessResponse;
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
public class TokenRefreshController {

    private final TokenRefreshService tokenRefreshService;

    // 토큰갱신은 기존 refresh token을 폐기하고 새 토큰을 저장한느 상태 변경 작업이므로 POST 사용
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            // refresh token 누락은 인증 실패 401 이므로 required=false
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        try {
            AuthTokenPair tokens = tokenRefreshService.refresh(refreshToken);
            /*
            HTTP/1.1 200 OK
            Set-Cookie: access_token=...
            Set-Cookie: refresh_token=...
            Content-Type: application/json

            {
              "success": true
            }
             */
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.accessToken(tokens.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.refreshToken(tokens.refreshToken()).toString())
                    .body(SuccessResponse.completed());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.UNAUTHORIZED) {
                throw exception;
            }
            return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.clearAccessToken().toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.clearRefreshToken().toString())
                    .body(ErrorResponse.of(
                            ErrorCode.UNAUTHORIZED.getCode(),
                            ErrorCode.UNAUTHORIZED.getMessage()
                    ));
        }
    }
}
