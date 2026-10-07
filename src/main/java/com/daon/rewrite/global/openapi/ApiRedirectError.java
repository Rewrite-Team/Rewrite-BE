package com.daon.rewrite.global.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * OAuth처럼 JSON 본문 대신 redirect로 전달하는 오류의 상태·코드·처리 방법을 선언한다.
 * 커스터마이저는 기존 redirect 응답에 설명을 합치며 이 오류를 ErrorResponse JSON 예시로 만들지 않는다.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiRedirectError {

    String code();

    int status() default 302;

    String condition();

    String action();
}
