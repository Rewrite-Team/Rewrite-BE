package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 목록 SSE의 전체 스냅샷과 단건 변경이 공유하는 자기소개서 상태 정보.
 * displayStatus와 latestReviewedVersionId를 함께 보내 재첨삭 실패 시에도 이전 성공 결과를 식별할 수 있게 한다.
 */
public final class CoverLetterReviewStatusEventResponse {

    private CoverLetterReviewStatusEventResponse() {
    }

    @Schema(name = "CoverLetterReviewStatusSnapshot", requiredProperties = "items")
    public record Snapshot(List<Item> items) {
        public Snapshot {
            items = List.copyOf(items);
        }

        public static Snapshot from(List<CoverLetter> coverLetters) {
            return new Snapshot(coverLetters.stream().map(Item::from).toList());
        }
    }

    @Schema(name = "CoverLetterReviewStatusItem", requiredProperties = {"coverLetterId", "displayStatus", "latestReviewedVersionId"})
    public record Item(
            String coverLetterId,
            CoverLetterStatus displayStatus,
            @Schema(nullable = true)
            String latestReviewedVersionId
    ) {
        public static Item from(CoverLetter coverLetter) {
            return new Item(
                    coverLetter.getId(),
                    coverLetter.getStatus(),
                    coverLetter.getLatestReviewedVersionId()
            );
        }
    }
}
