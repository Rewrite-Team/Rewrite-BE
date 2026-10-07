package com.daon.rewrite.global.exception;

import com.daon.rewrite.global.response.ErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * controller 처리 중 발생한 오류를 공통 ErrorResponse로 변환한다.
 * 보안 필터에서 끝나는 인증·CSRF 오류는 SecurityErrorWriter가, 비동기 작업 실패는 각 Job 처리 경계가 담당한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        e.getMessage(),
                        e.getDetails()
                ));
    }

    // 요청 DTO의 Bean Validation 실패를 필드별 사유로 모아 서비스 검증 오류와 같은 응답 형식으로 반환한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<ErrorResponse.ErrorDetail> details = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ErrorResponse.ErrorDetail(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        ErrorCode.VALIDATION_ERROR.getMessage(),
                        details
                ));
    }

    // controller 인자를 변환하지 못한 요청도 입력 오류로 분류한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        ErrorCode.VALIDATION_ERROR.getMessage()
                ));
    }

    // 분류되지 않은 예외의 원문을 응답에 노출하지 않고 공통 내부 오류 메시지만 반환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()
                ));
    }
}
