package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;

import java.util.List;

public record CoverLetterDetailResult(
        CoverLetter coverLetter,
        ReviewVersionResult reviewVersion,
        LlmJob reviewJob,
        List<QuestionResult> questions
) {
    public record ReviewVersionResult(
            ReviewVersion value,
            boolean latest
    ) {
    }

    public record QuestionResult(
            String questionResultId,
            String questionId,
            int order,
            String question,
            Integer maxAnswerLength,
            String originalAnswer,
            Integer originalAnswerLength,
            String aiReport,
            String rewrittenAnswer,
            Integer rewrittenAnswerLength,
            String finalAnswer,
            Integer finalAnswerLength
    ) {
    }
}
