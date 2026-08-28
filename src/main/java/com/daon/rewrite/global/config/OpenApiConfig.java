package com.daon.rewrite.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI rewriteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rewrite API")
                        .description("Rewrite 백엔드 구현 확인용 API 문서")
                        .version("v1"));
    }
}
