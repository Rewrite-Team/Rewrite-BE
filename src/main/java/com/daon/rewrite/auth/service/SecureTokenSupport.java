package com.daon.rewrite.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class SecureTokenSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokenSupport() {
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        // java.util.Random 은 통계적 난수 생성기이기에 내부 상태가 추측되면 다음 값을 예측 할 수 있음.
        // 따라서 SecureRandom으로 보안 목적의 난수 생성
        SECURE_RANDOM.nextBytes(bytes);
        // byte 배열은 URL이나 쿠키에 넣기 불편하기에 문자열로 변환 진행
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /*
    토큰 원본을 데이터베이스에 저장하지 않고 해시만 저장한다.
    토큰 검증 과정에서도 원본으로 비교하는 것이 아닌 해시값 비교를 통해 검증을 한다.
    DB 가 유출되더라도 저장된 해시만 가지고 유효한 원본 토큰을 바로 사용할 수 없게 된다.(SHA-256만 저장해도 원본을 역추적 불가능)
     */

    // 입력 문자열을 UTF-8 바이트로 바꾸고 SHA-256으로 해석한 뒤, 32바이트 결과를 64자리 16진수 문자열로 바꿔 반환
    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")    // SHA-256은 어떤 길이의 데이터를 입력받더라도 항상 256비트 결과를 만든다.
                    // SHA-256은 Java String 객체를 직접 처리하지 않는다. 실제 바이트 데이터를 입력받기 때문에 먼저 문자열을 byte[]로 변환 진행 후 digest()가 입력 바이트를 SHA-256으로 계산
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            // HexFormat은 byte[] 를 사람이 다루기 쉬운 16진수 문자열 64글자로 변환(256byte=16진수(4byte) * 64글자)
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
