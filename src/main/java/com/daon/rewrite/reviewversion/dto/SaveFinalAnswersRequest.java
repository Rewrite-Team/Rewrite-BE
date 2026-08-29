package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SaveFinalAnswersRequest(
        @ArraySchema(
                arraySchema = @Schema(
                        description = "첨삭 버전에 포함된 모든 문항의 최종 작성본",
                        requiredMode = Schema.RequiredMode.REQUIRED
                ),
                schema = @Schema(implementation = AnswerRequest.class)
        )
        List<AnswerRequest> answers
) {

    public List<SaveFinalAnswerInput> toInputs() {
        if (answers == null) {
            return null;
        }
        return answers.stream()
                .map(answer -> answer == null
                        ? null
                        : new SaveFinalAnswerInput(
                                answer.questionResultId(),
                                answer.finalAnswer()
                        ))
                .toList();
    }

    public record AnswerRequest(
            @Schema(
                    description = "저장할 첨삭 문항 결과 ID",
                    example = "rvqr_123e4567-e89b-12d3-a456-426614174000",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String questionResultId,
            @Schema(
                    description = "최종 작성본. trim 후 1자 이상 5000자 이하",
                    example = "사용자의 글쓰기 과정을 더 편리하게 만드는 Rewrite의 목표에 공감해 지원했습니다. "
                            + "Spring Boot 기반 API 설계와 인증 흐름 구현 경험을 활용해 "
                            + "안전하고 신뢰할 수 있는 자기소개서 첨삭 서비스를 만들겠습니다.",
                    minLength = 1,
                    maxLength = 5000,
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String finalAnswer
    ) {
    }
}
