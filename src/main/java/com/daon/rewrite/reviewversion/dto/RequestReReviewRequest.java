package com.daon.rewrite.reviewversion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RequestReReviewRequest(
        @Schema(
                description = "재첨삭 요구사항. 값이 있으면 trim 후 최대 1000자",
                example = "백엔드 개발 경험과 문제 해결 과정이 더 구체적으로 드러나도록 다듬어 주세요.",
                maxLength = 1000,
                nullable = true
        )
        String requestInstruction
) {
}
