package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record CreateCoverLetterResponse(
        String id,
        CoverLetterStatus status,
        LocalDateTime createdAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static CreateCoverLetterResponse from(CoverLetter coverLetter) {
        return new CreateCoverLetterResponse(
                coverLetter.getId(),
                coverLetter.getStatus(),
                LocalDateTime.ofInstant(coverLetter.getCreatedAt(), API_ZONE)
        );
    }
}
