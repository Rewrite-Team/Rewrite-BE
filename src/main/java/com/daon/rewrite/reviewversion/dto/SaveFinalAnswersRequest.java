package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;

import java.util.List;

public record SaveFinalAnswersRequest(
        List<AnswerRequest> answers
) {

    public List<SaveFinalAnswerInput> toInputs() {
        if (answers == null) {
            return null;
        }
        return answers.stream()
                .map(answer -> answer == null
                        ? null
                        : new SaveFinalAnswerInput(
                                answer.questionResultId(),
                                answer.finalAnswer()
                        ))
                .toList();
    }

    public record AnswerRequest(
            String questionResultId,
            String finalAnswer
    ) {
    }
}
