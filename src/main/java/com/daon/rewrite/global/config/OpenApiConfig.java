package com.daon.rewrite.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String CSRF_SECURITY_SCHEME = "csrfToken";

    @Bean
    OpenAPI rewriteOpenApi() {
        return new OpenAPI()
                // swagger 화면 상단에 표시할 제목, 설명, 버전 설정
                .info(new Info()
                        .title("Rewrite API")
                        .description("Rewrite 백엔드 구현 확인용 API 문서")
                        .version("v1"))
                // swagger의 Authorize 창에 csrfToken 입력란 생성
                .schemaRequirement(CSRF_SECURITY_SCHEME, new SecurityScheme()   // csrfToken: OpenAPI 문서 내부에서 사용하는 인증 방식 이름
                        .type(SecurityScheme.Type.APIKEY)   // APIKEY: 사용자가 입력한 값을 요청에 첨부
                        .in(SecurityScheme.In.HEADER)   // HEADER: HTTP 헤더로 전달
                        .name("X-CSRF-Token")   // X-CSRF-Token: 실제 헤더 이름
                        .description("GET /auth/csrf-token 응답의 csrfToken 값"));
    }

    /*
    Springdoc이 컨트롤러를 분석해 OpenAPI 문서를 만든 다음, 모든 API 경로를 순회
    아래 HTTP 메서드에 csrfToken 인증 조건을 자동으로 추가
     */
    @Bean
    OpenApiCustomizer csrfSecurityRequirementCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem -> {
            if (pathItem.getPost() != null) {
                pathItem.getPost().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getPut() != null) {
                pathItem.getPut().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getPatch() != null) {
                pathItem.getPatch().addSecurityItem(csrfSecurityRequirement());
            }
            if (pathItem.getDelete() != null) {
                pathItem.getDelete().addSecurityItem(csrfSecurityRequirement());
            }
        });
    }

    private SecurityRequirement csrfSecurityRequirement() {
        return new SecurityRequirement().addList(CSRF_SECURITY_SCHEME);
    }
}
