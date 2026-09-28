package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties = "id")
public record CreateCoverLetterResponse(
        String id
) {
    public static CreateCoverLetterResponse from(CoverLetter coverLetter) {
        return new CreateCoverLetterResponse(coverLetter.getId());
    }
}
