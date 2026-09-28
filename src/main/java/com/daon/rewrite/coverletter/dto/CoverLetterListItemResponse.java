package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Schema(requiredProperties = {"id", "title", "companyName", "positionTitle", "displayStatus", "createdAt", "latestReviewedVersionId"})
public record CoverLetterListItemResponse(
        String id,
        @Schema(nullable = true)
        String title,
        @Schema(nullable = true)
        String companyName,
        @Schema(nullable = true)
        String positionTitle,
        CoverLetterStatus displayStatus,
        LocalDateTime createdAt,
        @Schema(nullable = true)
        String latestReviewedVersionId
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static CoverLetterListItemResponse from(CoverLetter coverLetter) {
        return new CoverLetterListItemResponse(
                coverLetter.getId(),
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getStatus(),
                LocalDateTime.ofInstant(coverLetter.getCreatedAt(), API_ZONE),
                coverLetter.getLatestReviewedVersionId()
        );
    }
}
