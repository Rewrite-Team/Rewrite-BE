package com.daon.rewrite.coverletter.entity;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CoverLetter {

    private final String id;
    private final String ownerId;
    private final CoverLetterStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CoverLetter(String id, String ownerId, CoverLetterStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static CoverLetter draft(String id, String ownerId, LocalDateTime now) {
        return new CoverLetter(id, ownerId, CoverLetterStatus.DRAFT, now);
    }
}
