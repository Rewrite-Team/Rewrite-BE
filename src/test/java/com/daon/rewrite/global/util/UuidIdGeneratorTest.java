package com.daon.rewrite.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidIdGeneratorTest {

    private final UuidIdGenerator idGenerator = new UuidIdGenerator();

    @Test
    void generateReturnsPrefixAndUuid() {
        String id = idGenerator.generate("cl");

        assertThat(id).startsWith("cl_");
        assertThat(id.substring("cl_".length())).isNotBlank();
    }
}
