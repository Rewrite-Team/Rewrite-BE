package com.daon.rewrite.reviewversion.client;

import java.util.List;

public record ReviewRequest(
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl,
        String preferences,
        String requestInstruction,
        List<ReviewQuestion> questions
) {

    public ReviewRequest {
        questions = List.copyOf(questions);
    }

    public ReviewRequest(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl,
            String preferences,
            List<ReviewQuestion> questions
    ) {
        this(title, companyName, positionTitle, jobPostingUrl, preferences, null, questions);
    }
}
