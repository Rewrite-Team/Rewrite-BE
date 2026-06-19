package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.service.SaveQuestionInput;

import java.util.List;

public record SaveQuestionsRequest(
        List<QuestionRequest> questions
) {

    public List<SaveQuestionInput> toInputs() {
        if (questions == null) {
            return null;
        }
        return questions.stream()
                .map(question -> question == null
                        ? null
                        : new SaveQuestionInput(
                                question.question(),
                                question.maxAnswerLength(),
                                question.originalAnswer()
                        ))
                .toList();
    }

    public record QuestionRequest(
            String question,
            Integer maxAnswerLength,
            String originalAnswer
    ) {
    }
}
