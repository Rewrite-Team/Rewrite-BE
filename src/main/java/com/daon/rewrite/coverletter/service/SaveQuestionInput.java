package com.daon.rewrite.coverletter.service;

public record SaveQuestionInput(
        String question,
        Integer maxAnswerLength,
        String originalAnswer
) {
}
