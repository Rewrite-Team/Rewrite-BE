package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.entity.CoverLetter;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record CoverLetterListItemResponse(
        String id,
        String title,
        String companyName,
        String positionTitle,
        CoverLetterStatus displayStatus,
        LocalDateTime createdAt,
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
