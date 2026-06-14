package com.daon.rewrite.coverletter.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidCoverLetterIdGenerator implements CoverLetterIdGenerator {

    @Override
    public String generate() {
        return "cl_" + UUID.randomUUID();
    }
}
