package com.daon.rewrite.global.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RewriteApi {

    String id();

    String summary();

    String tag();

    String purpose();

    String[] screens();

    String trigger();

    String behavior();

    String success();

    int successStatus() default 200;

    boolean authenticated() default true;

    boolean csrfProtected() default false;

    boolean includeInternalError() default true;

    ApiError[] errors() default {};

    ApiRedirectError[] redirectErrors() default {};
}
