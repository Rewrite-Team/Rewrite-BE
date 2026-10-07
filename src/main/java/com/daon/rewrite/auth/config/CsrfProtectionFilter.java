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

/**
 * 인증 Cookie가 자동 전송되는 상태 변경 요청에서 CSRF 헤더의 서명과 만료를 검증한다.
 * {@link AuthSecurityConfig}가 접근 허용 판단 뒤에 배치하므로 보호 경로는 인증 실패가 우선한다.
 * access 인증이 필요 없는 refresh·logout도 상태 변경 요청이므로 검증 대상이다.
 */
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
        if (PROTECTED_METHODS.contains(request.getMethod())
                && !csrfTokenService.isValid(request.getHeader(CSRF_HEADER))) {
            // 필터에서 응답을 완료해 토큰이 잘못된 요청이 controller에 도달하지 않게 한다.
            SecurityErrorWriter.write(response, ErrorCode.CSRF_TOKEN_INVALID);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
