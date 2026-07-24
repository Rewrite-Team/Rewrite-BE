package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;

import java.util.List;

public final class CoverLetterReviewStatusEventResponse {

    private CoverLetterReviewStatusEventResponse() {
    }

    public record Snapshot(List<Item> items) {
        public Snapshot {
            items = List.copyOf(items);
        }

        public static Snapshot from(List<CoverLetter> coverLetters) {
            return new Snapshot(coverLetters.stream().map(Item::from).toList());
        }
    }

    public record Item(
            String coverLetterId,
            CoverLetterStatus displayStatus,
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
