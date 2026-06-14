package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.time.LocalDateTime;

public record CreateCoverLetterResponse(
        String id,
        CoverLetterStatus status,
        LocalDateTime createdAt
) {
    public static CreateCoverLetterResponse from(CoverLetter coverLetter) {
        return new CreateCoverLetterResponse(
                coverLetter.getId(),
                coverLetter.getStatus(),
                coverLetter.getCreatedAt()
        );
    }
}
