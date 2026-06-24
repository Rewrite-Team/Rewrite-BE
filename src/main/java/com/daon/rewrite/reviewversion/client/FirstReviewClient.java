package com.daon.rewrite.reviewversion.client;

import java.util.List;

public interface FirstReviewClient {

    List<FirstReviewResult> review(FirstReviewRequest request);
}
