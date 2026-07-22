package com.daon.rewrite.coverletter.dto;

public record SavePreferencesResponse(
        boolean success
) {
    public static SavePreferencesResponse completed() {
        return new SavePreferencesResponse(true);
    }
}
