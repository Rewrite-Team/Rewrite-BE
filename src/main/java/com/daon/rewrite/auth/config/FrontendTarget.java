package com.daon.rewrite.auth.config;

import java.util.Arrays;
import java.util.Optional;

/**
 * OAuth 결과를 돌려보낼 프론트엔드를 허용된 두 환경으로 제한한다.
 * 로그인 시작 때 선택한 값은 state에 결합되며, callback 검증 후 {@link AuthProperties}의 목적지를 선택한다.
 */
public enum FrontendTarget {
    LOCAL("local"),
    PRODUCTION("production");

    private final String value;

    FrontendTarget(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** 외부 target 문자열을 정확히 일치하는 값으로 변환하며, 미지원 값은 호출자가 실패 처리한다. */
    public static Optional<FrontendTarget> from(String value) {
        return Arrays.stream(values())
                .filter(target -> target.value.equals(value))
                .findFirst();
    }
}
