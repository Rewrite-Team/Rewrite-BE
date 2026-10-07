package com.daon.rewrite.global.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * controller 메서드에 API의 사용 맥락과 오류 정책을 선언하는 문서용 메타데이터다.
 * RewriteApiOperationCustomizer가 Springdoc의 HTTP 계약·DTO 스키마에 설명·오류 응답·x-rewrite 확장을 결합한다.
 * 인증·CSRF·내부 오류 플래그는 문서 구성을 제어하며 실제 보안 설정이나 예외 처리를 변경하지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RewriteApi {

    String id();

    String operationId();

    String summary();

    String tag();

    String purpose();

    String[] screens();

    String trigger();

    String behavior();

    String success();

    int successStatus() default 200;

    /** 인증 필요 표시와 공통 UNAUTHORIZED 오류 문서를 추가한다. */
    boolean authenticated() default true;

    /** CSRF 필요 표시와 공통 CSRF_TOKEN_INVALID 오류 문서를 추가한다. */
    boolean csrfProtected() default false;

    /** 예상하지 못한 서버 오류의 공통 INTERNAL_ERROR 문서를 추가한다. */
    boolean includeInternalError() default true;

    ApiError[] errors() default {};

    ApiRedirectError[] redirectErrors() default {};
}
