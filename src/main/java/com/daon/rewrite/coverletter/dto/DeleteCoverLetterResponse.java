package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record DeleteCoverLetterResponse(
        boolean success,
        LocalDateTime deletedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static DeleteCoverLetterResponse from(CoverLetter coverLetter) {
        return new DeleteCoverLetterResponse(
                true,
                LocalDateTime.ofInstant(coverLetter.getDeletedAt(), API_ZONE)
        );
    }
}
