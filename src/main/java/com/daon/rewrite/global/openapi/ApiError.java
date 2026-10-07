package com.daon.rewrite.global.openapi;

import com.daon.rewrite.global.exception.ErrorCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @RewriteApi}에서 JSON HTTP 오류의 코드·발생 조건·프론트엔드 처리를 선언한다.
 * 커스터마이저가 ErrorCode의 상태·메시지와 합쳐 오류 설명과 응답 예시를 만들며, 실제 예외 처리는 변경하지 않는다.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiError {

    ErrorCode code();

    String condition();

    String action();

    /** VALIDATION_ERROR의 대표 detail 예시에 사용할 필드이며 detailReason과 함께 지정한다. */
    String detailField() default "";

    String detailReason() default "";
}
