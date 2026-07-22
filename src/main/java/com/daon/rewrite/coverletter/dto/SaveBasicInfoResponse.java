package com.daon.rewrite.coverletter.dto;

public record SaveBasicInfoResponse(
        boolean success
) {
    public static SaveBasicInfoResponse completed() {
        return new SaveBasicInfoResponse(true);
    }
}
