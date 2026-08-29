package com.daon.rewrite.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SendInterviewMessageRequest(
        @Schema(
                description = "면접 질문에 대한 사용자 답변. trim 후 1자 이상 2000자 이하",
                example = "Rewrite 프로젝트에서 인증 API 설계와 구현을 담당했습니다. "
                        + "HttpOnly Cookie와 CSRF 토큰을 함께 사용해 보안을 강화했고, "
                        + "통합 테스트로 로그인과 토큰 갱신 흐름을 검증했습니다.",
                minLength = 1,
                maxLength = 2000,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {
}
