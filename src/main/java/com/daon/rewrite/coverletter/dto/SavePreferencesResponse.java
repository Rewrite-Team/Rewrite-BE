package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record SavePreferencesResponse(
        String id,
        String preferences,
        CoverLetterStatus status,
        LocalDateTime updatedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static SavePreferencesResponse from(CoverLetter coverLetter) {
        return new SavePreferencesResponse(
                coverLetter.getId(),
                coverLetter.getPreferences(),
                coverLetter.getStatus(),
                LocalDateTime.ofInstant(coverLetter.getUpdatedAt(), API_ZONE)
        );
    }
}
