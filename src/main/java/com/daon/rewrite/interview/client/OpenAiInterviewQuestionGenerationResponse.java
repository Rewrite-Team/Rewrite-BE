package com.daon.rewrite.interview.client;

import java.util.List;

record OpenAiInterviewQuestionGenerationResponse(List<Question> questions) {

    record Question(String question) {
    }
}
