package com.daon.rewrite.auth;

/**
 * 도메인 서비스가 인증 방식에 의존하지 않고 현재 사용자를 조회하는 경계.
 * {@code auth-real}에서는 검증된 JWT로 식별한 DB 사용자를, {@code auth-dev}에서는 고정 개발 사용자를 제공한다.
 */
public interface CurrentUserProvider {

    CurrentUser currentUser();
}
