package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.service.CoverLetterDetailResult;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmJobStateResponseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(LlmJobResultRefType.class)
    void queryAndStateEventKeepTheResultTypeString(LlmJobResultRefType resultType) {
        LlmJob job = LlmJob.pendingReview("job_test", "cl_test", NOW, 1);
        job.markCompleted(1, "완료", resultType, "result_test", NOW);

        LlmJobStateResponse response = LlmJobStateResponse.from(job);
        LlmJobStateEventResponse event = LlmJobStateEventResponse.from(job);

        assertThat(response.resultRef().type()).isEqualTo(resultType);
        assertThat(objectMapper.valueToTree(response).path("resultRef").path("type").asString())
                .isEqualTo(resultType.name());
        assertThat(objectMapper.valueToTree(event).path("resultRef").path("type").asString())
                .isEqualTo(resultType.name());
        assertThat(response.resultRef().id()).isEqualTo("result_test");
    }

    @ParameterizedTest
    @CsvSource({
            "LLM_PROVIDER_ERROR, LLM_PROVIDER_ERROR",
            "LLM_CONTEXT_LENGTH_EXCEEDED, LLM_CONTEXT_LENGTH_EXCEEDED",
            "LLM_CONTENT_FILTERED, LLM_CONTENT_FILTERED",
            "LLM_RESPONSE_INVALID, LLM_PROVIDER_ERROR",
            "UNEXPECTED_INTERNAL_CODE, LLM_PROVIDER_ERROR",
            "'', LLM_PROVIDER_ERROR"
    })
    void queryDetailAndStateEventShareThePublicJobError(
            String internalCode,
            LlmJobErrorCode expectedCode
    ) {
        CoverLetter coverLetter = CoverLetter.create("cl_test", "owner", NOW);
        LlmJob job = LlmJob.pendingReview("job_test", coverLetter.getId(), NOW, 1);
        job.markFailed(0, "실패", internalCode, "공개 실패 메시지", NOW);

        LlmJobStateResponse state = LlmJobStateResponse.from(job);
        LlmJobStateEventResponse event = LlmJobStateEventResponse.from(job);
        CoverLetterDetailResponse detail = CoverLetterDetailResponse.from(new CoverLetterDetailResult(
                coverLetter, null, job, List.of()
        ));

        assertThat(state.error().code()).isEqualTo(expectedCode);
        assertThat(event.error().code()).isEqualTo(expectedCode);
        assertThat(detail.reviewJob().error().code()).isEqualTo(expectedCode);
        assertPublicError(objectMapper.valueToTree(state).path("error"), expectedCode);
        assertPublicError(objectMapper.valueToTree(event).path("error"), expectedCode);
        assertPublicError(objectMapper.valueToTree(detail).path("reviewJob").path("error"), expectedCode);
        assertThat(job.getErrorCode()).isEqualTo(internalCode);
    }

    @Test
    void pendingJobKeepsNullResultAndErrorFields() {
        CoverLetter coverLetter = CoverLetter.create("cl_test", "owner", NOW);
        LlmJob job = LlmJob.pendingReview("job_test", coverLetter.getId(), NOW, 1);
        LlmJobStateResponse state = LlmJobStateResponse.from(job);
        CoverLetterDetailResponse detail = CoverLetterDetailResponse.from(new CoverLetterDetailResult(
                coverLetter, null, job, List.of()
        ));
        JsonNode json = objectMapper.valueToTree(state);

        assertThat(state.resultRef()).isNull();
        assertThat(state.error()).isNull();
        assertThat(detail.reviewJob().error()).isNull();
        assertThat(json.has("resultRef")).isTrue();
        assertThat(json.path("resultRef").isNull()).isTrue();
        assertThat(json.has("error")).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
    }

    private void assertPublicError(JsonNode error, LlmJobErrorCode expectedCode) {
        assertThat(error.path("code").asString()).isEqualTo(expectedCode.name());
        assertThat(error.path("message").asString()).isEqualTo("공개 실패 메시지");
    }
}
