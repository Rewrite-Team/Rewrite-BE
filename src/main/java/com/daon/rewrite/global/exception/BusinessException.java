package com.daon.rewrite.global.exception;

import com.daon.rewrite.global.response.ErrorResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorResponse.ErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorResponse.ErrorDetail> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }
}
