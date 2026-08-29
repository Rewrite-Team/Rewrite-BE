package com.daon.rewrite.coverletter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SaveBasicInfoRequest(
        @Schema(
                description = "자기소개서 제목. 값이 있으면 trim 후 최대 50자",
                example = "2026 상반기 Rewrite 백엔드 개발자 자기소개서",
                maxLength = 50,
                nullable = true
        )
        String title,
        @Schema(
                description = "지원 회사명. 값이 있으면 trim 후 최대 30자",
                example = "Rewrite",
                maxLength = 30,
                nullable = true
        )
        String companyName,
        @Schema(
                description = "지원 직무명. 값이 있으면 trim 후 최대 30자",
                example = "백엔드 개발자",
                maxLength = 30,
                nullable = true
        )
        String positionTitle,
        @Schema(
                description = "채용 공고 URL. 값이 있으면 trim 후 최대 500자",
                example = "https://recruit.example.com/jobs/backend-developer",
                format = "uri",
                maxLength = 500,
                nullable = true
        )
        String jobPostingUrl
) {
}
