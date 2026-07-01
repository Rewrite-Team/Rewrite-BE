package com.daon.rewrite.reviewversion.service;

public record SaveFinalAnswerInput(
        String questionResultId,
        String finalAnswer
) {
}
