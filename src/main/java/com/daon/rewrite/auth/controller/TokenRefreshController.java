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

/**
 * 토큰 회전 결과를 인증 Cookie 교체로 반영하는 HTTP 경계.
 * refresh 인증 실패 때만 두 Cookie를 만료시키며, 다른 오류는 기존 오류 처리 흐름으로 전달한다.
 */
@RestController
@Profile("auth-real")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TokenRefreshController {

    private final TokenRefreshService tokenRefreshService;

    // 기존 refresh token 폐기와 새 토큰 저장을 수반하는 상태 변경이므로 POST를 사용한다.
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
            operationId = "refreshAuthTokens",
            summary = "토큰 갱신",
            tag = "Auth",
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
            // Cookie 누락도 서비스의 UNAUTHORIZED 처리로 보내기 위해 바인딩 단계에서 필수로 요구하지 않는다.
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        try {
            AuthTokenPair tokens = tokenRefreshService.refresh(refreshToken);
            // 서비스 트랜잭션의 회전이 성공한 뒤에만 브라우저의 두 Cookie를 함께 교체한다.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.accessToken(tokens.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookieFactory.refreshToken(tokens.refreshToken()).toString())
                    .body(SuccessResponse.completed());
        } catch (BusinessException exception) {
            // 저장 실패 등 인증 외 오류에서 Cookie를 지워 복구 가능한 인증 상태를 잃지 않도록 한다.
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
