package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthCookieFactory;
import com.daon.rewrite.auth.service.AuthTokenPair;
import com.daon.rewrite.auth.service.TokenRefreshService;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.openapi.ApiError;
import com.daon.rewrite.global.openapi.RewriteApi;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"prod", "auth-test"})
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TokenRefreshController {

    private final TokenRefreshService tokenRefreshService;

    // 토큰갱신은 기존 refresh token을 폐기하고 새 토큰을 저장한느 상태 변경 작업이므로 POST 사용
    @PostMapping("/refresh")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 회전 완료",
                    headers = @Header(name = "Set-Cookie", description = "새 access_token·refresh_token", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SuccessResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "refresh token 인증 실패",
                    headers = @Header(name = "Set-Cookie", description = "access_token·refresh_token 만료", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @RewriteApi(
            id = "API-004",
            summary = "토큰 갱신",
            tag = "인증",
            purpose = "refresh token을 원자적으로 회전하고 새 인증 Cookie를 발급한다.",
            screens = "로그인",
            trigger = "보호 API의 401을 감지했을 때 여러 요청을 single-flight로 묶어 한 번 호출한다.",
            behavior = "기존 refresh token을 폐기하고 access_token·refresh_token Cookie를 함께 교체한다. 로그아웃 흐름과 직렬화한다.",
            success = "대기 중인 원 요청을 각각 한 번만 재시도한다.",
            authenticated = false,
            csrfProtected = true,
            errors = @ApiError(
                    code = ErrorCode.UNAUTHORIZED,
                    condition = "refresh token 누락·만료·위조·폐기·재사용",
                    action = "인증 상태를 정리하고 로그인 화면으로 이동하며 refresh를 반복하지 않는다."
            )
    )
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
