package com.daon.rewrite.reviewversion.client;

public interface ReviewClient {

    ReviewResult reviewQuestion(ReviewRequest request, String targetQuestionId);
}
