package com.daon.rewrite.reviewversion.client;

import java.util.List;

/**
 * 문항 하나를 첨삭할 때도 함께 전달하는 전체 자기소개서 문맥과 선택 요구사항이다.
 * 최초 첨삭은 제출 원본 문항을, 재첨삭은 Job 시작 요청에서 고정한 답변 스냅샷을 questions로 사용한다.
 */
public record ReviewRequest(
        String title,
        String companyName,
        String positionTitle,
        String jobPostingUrl,
        String preferences,
        String requestInstruction,
        List<ReviewQuestion> questions
) {

    public ReviewRequest {
        questions = List.copyOf(questions);
    }

    public ReviewRequest(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl,
            String preferences,
            List<ReviewQuestion> questions
    ) {
        this(title, companyName, positionTitle, jobPostingUrl, preferences, null, questions);
    }
}
