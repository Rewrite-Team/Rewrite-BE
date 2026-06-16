package com.daon.rewrite.global.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidIdGenerator implements IdGenerator {

    @Override
    public String generate(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }
}
