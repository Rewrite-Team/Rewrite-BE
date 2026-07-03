package com.daon.rewrite.reviewversion.client;

import java.util.List;

public record FirstReviewRequest(
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl,
        String preferences,
        String requestInstruction,
        List<FirstReviewQuestion> questions
) {

    public FirstReviewRequest {
        questions = List.copyOf(questions);
    }

    public FirstReviewRequest(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl,
            String preferences,
            List<FirstReviewQuestion> questions
    ) {
        this(title, companyName, positionTitle, jobPostingUrl, preferences, null, questions);
    }
}
