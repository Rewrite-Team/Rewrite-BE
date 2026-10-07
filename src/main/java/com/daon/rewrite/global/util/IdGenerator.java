package com.daon.rewrite.global.util;

/** 자원 종류를 나타내는 prefix를 받아 ID 생성 방식을 도메인 서비스에서 분리한다. */
public interface IdGenerator {

    String generate(String prefix);
}
