package com.daon.rewrite.keywordanalysis.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.service.LatestKeywordAnalysisResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 분석 리소스가 없으면 응답에서만 NOT_STARTED로 표현한다.
 * 결과 노출 여부와 진행·실패 Job 선택은 서비스가 결정하며, 재분석 중·실패 시 keywords는 빈 목록이다.
 */
@Schema(requiredProperties = {"coverLetter", "sourceReviewVersion", "status", "jobId", "keywords"})
public record LatestKeywordAnalysisResponse(
        CoverLetterResponse coverLetter,
        @Schema(nullable = true)
        ReviewVersionResponse sourceReviewVersion,
        String status,
        @Schema(nullable = true)
        String jobId,
        List<KeywordResponse> keywords
) {

    private static final String NOT_STARTED_STATUS = "NOT_STARTED";

    public static LatestKeywordAnalysisResponse from(LatestKeywordAnalysisResult result) {
        if (result.keywordAnalysis() == null) {
            return new LatestKeywordAnalysisResponse(
                    CoverLetterResponse.from(result.coverLetter()),
                    null,
                    NOT_STARTED_STATUS,
                    null,
                    List.of()
            );
        }
        return new LatestKeywordAnalysisResponse(
                CoverLetterResponse.from(result.coverLetter()),
                new ReviewVersionResponse(
                        result.sourceReviewVersion().getId(),
                        result.sourceReviewVersion().getVersion()
                ),
                result.keywordAnalysis().getStatus().name(),
                result.job() == null ? null : result.job().getId(),
                result.keywords().stream()
                        .map(KeywordResponse::from)
                        .toList()
        );
    }

    @Schema(name = "KeywordAnalysisCoverLetterResponse", requiredProperties = {"id", "title", "companyName", "positionTitle"})
    public record CoverLetterResponse(
            String id,
            @Schema(nullable = true)
            String title,
            @Schema(nullable = true)
            String companyName,
            @Schema(nullable = true)
            String positionTitle
    ) {
        private static CoverLetterResponse from(CoverLetter coverLetter) {
            return new CoverLetterResponse(
                    coverLetter.getId(),
                    coverLetter.getTitle(),
                    coverLetter.getCompanyName(),
                    coverLetter.getPositionTitle()
            );
        }
    }

    @Schema(name = "KeywordAnalysisReviewVersionResponse", requiredProperties = {"id", "version"})
    public record ReviewVersionResponse(String id, String version) {
    }

    @Schema(requiredProperties = {"keyword", "importance"})
    private record KeywordResponse(
            String keyword,
            int importance
    ) {

        private static KeywordResponse from(KeywordAnalysisKeyword keyword) {
            return new KeywordResponse(keyword.getKeyword(), keyword.getImportance());
        }
    }
}
