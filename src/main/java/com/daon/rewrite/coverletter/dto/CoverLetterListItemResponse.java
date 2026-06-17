package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record CoverLetterListItemResponse(
        String id,
        String title,
        String companyName,
        String positionTitle,
        CoverLetterStatus status,
        LocalDateTime createdAt,
        String latestReviewVersionId
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
                coverLetter.getLatestReviewVersionId()
        );
    }
}
