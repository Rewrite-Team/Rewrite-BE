package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.auth.dto.CurrentUserResponse;
import com.daon.rewrite.global.openapi.RewriteApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    @RewriteApi(
            id = "API-005",
            summary = "내 정보 조회",
            tag = "인증",
            purpose = "현재 access token으로 로그인한 사용자의 앱 사용자 정보를 조회한다.",
            screens = "로그인",
            trigger = "앱 초기 진입이나 인증 상태 복구 후 사용자 상태를 구성할 때 호출한다.",
            behavior = "현재 인증 사용자만 조회하며 별도 도메인 오류는 없다.",
            success = "사용자 상태를 구성하고 인증이 필요한 화면을 표시한다."
    )
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentUserProvider.currentUser());
    }
}
