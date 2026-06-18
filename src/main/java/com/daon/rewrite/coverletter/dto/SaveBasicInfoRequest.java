package com.daon.rewrite.coverletter.dto;

public record SaveBasicInfoRequest(
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl
) {
}
