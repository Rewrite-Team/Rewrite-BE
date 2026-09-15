package com.daon.rewrite.auth.config;

import java.util.Arrays;
import java.util.Optional;

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

    public static Optional<FrontendTarget> from(String value) {
        return Arrays.stream(values())
                .filter(target -> target.value.equals(value))
                .findFirst();
    }
}
