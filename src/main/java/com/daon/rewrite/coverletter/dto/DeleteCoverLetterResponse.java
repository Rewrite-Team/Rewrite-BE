package com.daon.rewrite.coverletter.dto;

public record DeleteCoverLetterResponse(
        boolean success
) {
    public static DeleteCoverLetterResponse completed() {
        return new DeleteCoverLetterResponse(true);
    }
}
