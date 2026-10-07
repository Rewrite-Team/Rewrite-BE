package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.SendInterviewMessageResult;
import io.swagger.v3.oas.annotations.media.Schema;
/** 같은 트랜잭션에서 저장한 USER 메시지와 피드백 Job의 ID다. ASSISTANT 피드백은 비동기로 생성한다. */
@Schema(requiredProperties = {"userMessageId", "jobId"})
public record SendInterviewMessageResponse(
        String userMessageId,
        String jobId
) {

    public static SendInterviewMessageResponse from(SendInterviewMessageResult result) {
        return new SendInterviewMessageResponse(
                result.userMessage().getId(),
                result.job().getId()
        );
    }
}
