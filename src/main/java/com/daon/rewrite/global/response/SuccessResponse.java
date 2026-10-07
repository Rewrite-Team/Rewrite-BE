package com.daon.rewrite.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 별도 반환 데이터가 없는 저장·삭제 등의 API에서 성공 여부를 표시한다. */
@Schema(requiredProperties = "success")
public record SuccessResponse(
        boolean success
) {
    public static SuccessResponse completed() {
        return new SuccessResponse(true);
    }
}
