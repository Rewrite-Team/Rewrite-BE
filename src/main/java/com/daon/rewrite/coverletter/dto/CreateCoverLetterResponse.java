package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
public record CreateCoverLetterResponse(
        String id
) {
    public static CreateCoverLetterResponse from(CoverLetter coverLetter) {
        return new CreateCoverLetterResponse(coverLetter.getId());
    }
}
