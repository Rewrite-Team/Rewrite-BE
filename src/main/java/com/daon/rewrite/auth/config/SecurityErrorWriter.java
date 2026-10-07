package com.daon.rewrite.auth.config;

import com.daon.rewrite.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * controller에 도달하기 전 발생한 인증·CSRF 오류를 공통 ErrorResponse JSON 형식으로 작성한다.
 * 필터 오류는 controller 예외 처리 경계를 거치지 않으므로 보안 필터와 인증 진입점에서 직접 사용한다.
 */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":{\"code\":\"" + errorCode.getCode()
                        + "\",\"message\":\"" + errorCode.getMessage()
                        + "\",\"details\":[]}}"
        );
    }
}
