package com.daon.rewrite.reviewversion.client;

public interface FirstReviewClient {

    FirstReviewResult reviewQuestion(FirstReviewRequest request, String targetQuestionId);
}
