package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SaveQuestionsRequest(
        @ArraySchema(
                arraySchema = @Schema(
                        description = "현재 자기소개서 문항 전체. null 또는 빈 배열이면 기존 문항을 모두 삭제",
                        nullable = true
                ),
                schema = @Schema(implementation = QuestionRequest.class)
        )
        List<QuestionRequest> questions
) {

    public List<SaveQuestionInput> toInputs() {
        if (questions == null) {
            return null;
        }
        return questions.stream()
                .map(question -> question == null
                        ? null
                        : new SaveQuestionInput(
                                question.question(),
                                question.maxAnswerLength(),
                                question.originalAnswer()
                        ))
                .toList();
    }

    public record QuestionRequest(
            @Schema(
                    description = "자기소개서 문항. 값이 있으면 trim 후 최대 300자",
                    example = "Rewrite에 지원한 동기와 입사 후 이루고 싶은 목표를 작성해 주세요.",
                    maxLength = 300,
                    nullable = true
            )
            String question,
            @Schema(
                    description = "최대 답변 글자 수. 값이 있으면 100 이상 5000 이하",
                    example = "1000",
                    minimum = "100",
                    maximum = "5000",
                    nullable = true
            )
            Integer maxAnswerLength,
            @Schema(
                    description = "첨삭 전 원본 답변. 값이 있으면 trim 후 최대 5000자",
                    example = "사용자의 글쓰기 과정을 더 편리하게 만드는 Rewrite의 목표에 공감해 지원했습니다. "
                            + "Spring Boot 기반 API를 설계하고 안정적으로 운영한 경험을 바탕으로, "
                            + "사용자가 신뢰할 수 있는 자기소개서 첨삭 서비스를 만들겠습니다.",
                    maxLength = 5000,
                    nullable = true
            )
            String originalAnswer
    ) {
    }
}
