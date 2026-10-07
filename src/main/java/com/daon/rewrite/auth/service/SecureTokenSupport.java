package com.daon.rewrite.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 인증 흐름에서 사용하는 보안 난수 생성과 SHA-256 해시 변환을 제공한다.
 */
final class SecureTokenSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokenSupport() {
    }

    /**
     * 32바이트의 보안 난수를 URL과 Cookie에 넣을 수 있는 패딩 없는 Base64URL 문자열로 반환한다.
     */
    static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * UTF-8 입력의 SHA-256 결과를 64자리 16진수 문자열로 반환한다.
     * OAuth state·nonce와 refresh token 저장 시 원문 대신 이 값을 보관하고, 요청 원문도 같은 방식으로 해시해 조회·비교한다.
     */
    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
