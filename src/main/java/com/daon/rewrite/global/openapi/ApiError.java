package com.daon.rewrite.global.openapi;

import com.daon.rewrite.global.exception.ErrorCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiError {

    ErrorCode code();

    String condition();

    String action();

    String detailField() default "";

    String detailReason() default "";
}
