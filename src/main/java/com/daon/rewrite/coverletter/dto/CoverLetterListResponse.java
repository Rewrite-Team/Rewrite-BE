package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(requiredProperties = {"items", "page", "size", "totalItems", "totalPages"})
public record CoverLetterListResponse(
        List<CoverLetterListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    // Spring Page의 내부 번호 대신 HTTP 요청의 1부터 시작하는 page를 응답에 유지한다.
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
