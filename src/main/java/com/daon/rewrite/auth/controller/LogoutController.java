package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.config.AuthCookieFactory;
import com.daon.rewrite.auth.service.LogoutService;
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
public class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/logout")
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
