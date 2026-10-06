package com.daon.rewrite.interview.client.question;

import java.util.List;

record OpenAiInterviewQuestionGenerationResponse(List<Question> questions) {

    record Question(String question) {
    }
}
