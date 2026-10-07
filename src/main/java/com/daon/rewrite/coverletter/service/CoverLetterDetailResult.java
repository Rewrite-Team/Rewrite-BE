package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;

import java.util.List;

/** 원본·임시·확정 문항을 현재 상세와 버전 상세가 공유하는 구조로 조합한 서비스 조회 결과다. */
public record CoverLetterDetailResult(
        CoverLetter coverLetter,
        ReviewVersionResult reviewVersion,
        LlmJob reviewJob,
        List<QuestionResult> questions
) {
    /** latest는 최신 시도, latestReviewed는 최신 성공 결과를 구분한다. */
    public record ReviewVersionResult(
            ReviewVersion value,
            boolean latest,
            boolean latestReviewed
    ) {
    }

    /** questionResultId는 확정된 버전 문항에만 있고, 원본·진행·실패 문항은 null이다. */
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
