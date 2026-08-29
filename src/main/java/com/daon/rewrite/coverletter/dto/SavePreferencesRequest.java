package com.daon.rewrite.coverletter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SavePreferencesRequest(
        @Schema(
                description = "채용 우대사항. 값이 있으면 trim 후 최대 3000자",
                example = "Java와 Spring Boot 기반 서비스 개발 경험, REST API 설계 경험, "
                        + "관계형 데이터베이스와 JPA 활용 경험을 우대합니다.",
                maxLength = 3000,
                nullable = true
        )
        String preferences
) {
}
