package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import org.springframework.data.domain.Page;

import java.util.List;

public record CoverLetterListResponse(
        List<CoverLetterListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static CoverLetterListResponse from(
            Page<CoverLetter> pageResult,
            int requestedPage,
            int requestedSize
    ) {
        return new CoverLetterListResponse(
                pageResult.getContent()
                        .stream()
                        .map(CoverLetterListItemResponse::from)
                        .toList(),
                requestedPage,
                requestedSize,
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }
}
