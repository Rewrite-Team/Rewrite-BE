package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.service.SaveQuestionsResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record SaveQuestionsResponse(
        String coverLetterId,
        List<QuestionResponse> questions,
        LocalDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static SaveQuestionsResponse from(SaveQuestionsResult result) {
        return new SaveQuestionsResponse(
                result.coverLetter().getId(),
                result.questions().stream()
                        .map(QuestionResponse::from)
                        .toList(),
                LocalDateTime.ofInstant(result.coverLetter().getUpdatedAt(), API_ZONE)
        );
    }

    public record QuestionResponse(
            String id,
            int order,
            String question,
            int maxAnswerLength,
            String originalAnswer
    ) {
        private static QuestionResponse from(CoverLetterQuestion question) {
            return new QuestionResponse(
                    question.getId(),
                    question.getQuestionOrder(),
                    question.getQuestion(),
                    question.getMaxAnswerLength(),
                    question.getOriginalAnswer()
            );
        }
    }
}
