package com.daon.rewrite.auth.service;

import java.net.URI;

public record KakaoAuthorizeResult(
        URI authorizeUri,
        String browserNonce
) {
}
