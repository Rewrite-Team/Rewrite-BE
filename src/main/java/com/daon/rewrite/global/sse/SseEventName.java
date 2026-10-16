package com.daon.rewrite.global.sse;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/** SSE의 event 필드에 사용하는 공개 이름. 데이터 payload와 전송 형식은 각 스트림이 결정한다. */
@Schema(enumAsRef = true)
public enum SseEventName {
    JOB_STATE("job.state"),
    REVIEW_QUESTIONS("review.questions"),
    INTERVIEW_FEEDBACK_DELTA("interview.feedback.delta"),
    COVER_LETTER_REVIEW_STATUS_SNAPSHOT("cover-letter.review-status.snapshot"),
    COVER_LETTER_REVIEW_STATUS_CHANGED("cover-letter.review-status.changed"),
    HEARTBEAT("heartbeat");

    private final String value;

    SseEventName(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
