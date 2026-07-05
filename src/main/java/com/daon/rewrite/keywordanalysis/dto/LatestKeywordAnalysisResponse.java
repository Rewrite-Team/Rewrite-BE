package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record LatestKeywordAnalysisResponse(
        String coverLetterId,
        KeywordAnalysisDetailResponse keywordAnalysis
) {

    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static LatestKeywordAnalysisResponse from(LatestKeywordAnalysisResult result) {
        if (result.keywordAnalysis() == null) {
            return new LatestKeywordAnalysisResponse(result.coverLetterId(), null);
        }
        return new LatestKeywordAnalysisResponse(
                result.coverLetterId(),
                KeywordAnalysisDetailResponse.from(result.keywordAnalysis(), result.keywords())
        );
    }

    private record KeywordAnalysisDetailResponse(
            String id,
            String coverLetterId,
            String sourceReviewVersionId,
            String status,
            List<KeywordResponse> keywords,
            ErrorResponse error,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {

        private static KeywordAnalysisDetailResponse from(
                KeywordAnalysis keywordAnalysis,
                List<KeywordAnalysisKeyword> keywords
        ) {
            return new KeywordAnalysisDetailResponse(
                    keywordAnalysis.getId(),
                    keywordAnalysis.getCoverLetter().getId(),
                    keywordAnalysis.getSourceReviewVersionId(),
                    keywordAnalysis.getStatus().name(),
                    keywords.stream()
                            .map(KeywordResponse::from)
                            .toList(),
                    ErrorResponse.from(keywordAnalysis),
                    LocalDateTime.ofInstant(keywordAnalysis.getCreatedAt(), API_ZONE),
                    keywordAnalysis.getCompletedAt() == null
                            ? null
                            : LocalDateTime.ofInstant(keywordAnalysis.getCompletedAt(), API_ZONE)
            );
        }
    }

    private record KeywordResponse(
            String keyword,
            int importance
    ) {

        private static KeywordResponse from(KeywordAnalysisKeyword keyword) {
            return new KeywordResponse(keyword.getKeyword(), keyword.getImportance());
        }
    }

    private record ErrorResponse(
            String code,
            String message
    ) {

        private static ErrorResponse from(KeywordAnalysis keywordAnalysis) {
            if (keywordAnalysis.getErrorCode() == null) {
                return null;
            }
            return new ErrorResponse(
                    keywordAnalysis.getErrorCode(),
                    keywordAnalysis.getErrorMessage()
            );
        }
    }
}
