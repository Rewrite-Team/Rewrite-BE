package com.daon.rewrite.global.config;

import com.daon.rewrite.global.openapi.RewriteApiOperationCustomizer;
import com.daon.rewrite.global.response.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
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
                        .description("API 번호, 사용 화면, 호출 흐름과 오류 조건을 확인하는 Rewrite 백엔드 핵심 API 문서")
                        .version("v1"))
                // swagger의 Authorize 창에 csrfToken 입력란 생성
                .schemaRequirement(CSRF_SECURITY_SCHEME, new SecurityScheme()   // csrfToken: OpenAPI 문서 내부에서 사용하는 인증 방식 이름
                        .type(SecurityScheme.Type.APIKEY)   // APIKEY: 사용자가 입력한 값을 요청에 첨부
                        .in(SecurityScheme.In.HEADER)   // HEADER: HTTP 헤더로 전달
                        .name("X-CSRF-Token")   // X-CSRF-Token: 실제 헤더 이름
                        .description("GET /auth/csrf-token 응답의 csrfToken 값"));
    }

    // Springdoc이 Controller의 각 API 메서드를 OpenAPI operation으로 변환할 때 호출한 customizer를 등록
    @Bean
    OperationCustomizer rewriteApiOperationCustomizer() {
        return new RewriteApiOperationCustomizer();
    }

    // 공통 오류 응답 DTO를 OpenAPI의 재사용 가능한 schema로 등록 (대상 클래스: ErrorResponse.java)
    @Bean
    OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            // OpenAPI components영역이 아직 생성되지 않았다면 새로 만든다.
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            // swagger의 모델 변환기가 Java record를 읽고 OpenAPI schema로 변환
            ModelConverters.getInstance()
                    .readAll(ErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);
        };
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
