package com.daon.rewrite.auth.client;

public interface KakaoClient {

    KakaoUser getUser(String authorizationCode);
}
