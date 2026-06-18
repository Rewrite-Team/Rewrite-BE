package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record SaveBasicInfoResponse(
        String id,
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl,
        CoverLetterStatus status,
        LocalDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static SaveBasicInfoResponse from(CoverLetter coverLetter) {
        return new SaveBasicInfoResponse(
                coverLetter.getId(),
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getJobPostingUrl(),
                coverLetter.getStatus(),
                LocalDateTime.ofInstant(coverLetter.getUpdatedAt(), API_ZONE)
        );
    }
}
