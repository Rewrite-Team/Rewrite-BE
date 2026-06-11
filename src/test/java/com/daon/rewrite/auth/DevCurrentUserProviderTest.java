package com.daon.rewrite.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DevCurrentUserProviderTest {

    @Test
    void currentUserReturnsFixedDevelopmentUser() {
        CurrentUserProvider provider = new DevCurrentUserProvider();

        CurrentUser currentUser = provider.currentUser();

        assertEquals("user_dev_001", currentUser.id());
        assertEquals("개발 사용자", currentUser.nickname());
        assertEquals("https://dev-profile.png", currentUser.profileImageUrl());
    }
}