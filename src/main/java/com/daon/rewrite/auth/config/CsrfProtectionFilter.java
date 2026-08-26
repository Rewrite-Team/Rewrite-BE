package com.daon.rewrite.auth.config;

import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

final class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfTokenService csrfTokenService;

    CsrfProtectionFilter(CsrfTokenService csrfTokenService) {
        this.csrfTokenService = csrfTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (PROTECTED_METHODS.contains(request.getMethod()) // POST, PUT, PATCH, DELETE 이면서
                && !csrfTokenService.isValid(request.getHeader(CSRF_HEADER))) { // CSRF 토큰이 유효하지 않다면
            SecurityErrorWriter.write(response, ErrorCode.CSRF_TOKEN_INVALID);  // 403 CSRF_TOKEN_INVALID 작성
            return; // 요청 처리 흐름 종료, controller 까지 가지 않음
        }
        filterChain.doFilter(request, response);    // 다음 필터에게 요청/응답을 넘겨 계속 처리 진행
    }
}
