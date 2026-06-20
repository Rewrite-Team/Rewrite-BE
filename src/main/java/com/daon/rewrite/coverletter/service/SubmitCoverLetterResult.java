package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record SubmitCoverLetterResult(
        CoverLetter coverLetter,
        LlmJob job
) {
}
