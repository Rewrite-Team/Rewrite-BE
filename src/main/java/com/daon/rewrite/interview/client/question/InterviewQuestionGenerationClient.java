package com.daon.rewrite.interview.client.question;

import java.util.List;

public interface InterviewQuestionGenerationClient {

    List<InterviewQuestionGenerationResult> generate(InterviewQuestionGenerationRequest request);
}
