package com.daon.rewrite.auth.controller;

import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.auth.dto.CurrentUserResponse;
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
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentUserProvider.currentUser());
    }
}
