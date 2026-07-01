package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswersResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record SaveFinalAnswersResponse(
        String coverLetterId,
        String reviewVersionId,
        List<QuestionResultResponse> questionResults,
        LocalDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static SaveFinalAnswersResponse from(SaveFinalAnswersResult result) {
        return new SaveFinalAnswersResponse(
                result.coverLetterId(),
                result.reviewVersionId(),
                result.questionResults().stream()
                        .map(QuestionResultResponse::from)
                        .toList(),
                LocalDateTime.ofInstant(result.updatedAt(), API_ZONE)
        );
    }

    public record QuestionResultResponse(
            String questionResultId,
            String finalAnswer,
            int finalAnswerLength
    ) {

        private static QuestionResultResponse from(ReviewVersionQuestionResult questionResult) {
            return new QuestionResultResponse(
                    questionResult.getId(),
                    questionResult.getFinalAnswer(),
                    questionResult.getFinalAnswerLength()
            );
        }
    }
}
