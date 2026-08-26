package com.daon.rewrite.auth.service;

public record OAuthStateIssue(
        String state,
        String browserNonce
) {
}
