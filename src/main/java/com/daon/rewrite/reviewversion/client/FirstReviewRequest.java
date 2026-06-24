package com.daon.rewrite.reviewversion.client;

import java.util.List;

public record FirstReviewRequest(
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl,
        String preferences,
        List<FirstReviewQuestion> questions
) {

    public FirstReviewRequest {
        questions = List.copyOf(questions);
    }
}
