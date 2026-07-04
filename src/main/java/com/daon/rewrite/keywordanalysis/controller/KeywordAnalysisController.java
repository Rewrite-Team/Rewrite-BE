package com.daon.rewrite.keywordanalysis.controller;

import com.daon.rewrite.keywordanalysis.dto.StartKeywordAnalysisRequest;
import com.daon.rewrite.keywordanalysis.dto.StartKeywordAnalysisResponse;
import com.daon.rewrite.keywordanalysis.service.KeywordAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KeywordAnalysisController {

    private final KeywordAnalysisService keywordAnalysisService;

    @PostMapping("/cover-letters/{coverLetterId}/keyword-analysis")
    public StartKeywordAnalysisResponse startKeywordAnalysis(
            @PathVariable String coverLetterId,
            @RequestBody(required = false) StartKeywordAnalysisRequest request
    ) {
        return StartKeywordAnalysisResponse.from(
                keywordAnalysisService.startMyKeywordAnalysis(
                        coverLetterId,
                        request == null ? null : request.sourceReviewVersionId()
                )
        );
    }
}
