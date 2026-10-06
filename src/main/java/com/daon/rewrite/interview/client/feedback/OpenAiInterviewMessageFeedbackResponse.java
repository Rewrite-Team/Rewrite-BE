package com.daon.rewrite.interview.client.feedback;

import java.util.List;

record OpenAiInterviewMessageFeedbackResponse(
        String content,
        Feedback feedback,
        Integer score,
        String followUpQuestion
) {

    record Feedback(
            String summary,
            List<String> strengths,
            List<String> improvements
    ) {
    }
}
