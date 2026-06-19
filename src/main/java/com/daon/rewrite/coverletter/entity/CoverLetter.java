package com.daon.rewrite.coverletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cover_letters")
public class CoverLetter {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "company_name", length = 30)
    private String companyName;

    @Column(name = "position_title", length = 30)
    private String positionTitle;

    @Column(name = "job_posting_url", length = 500)
    private String jobPostingUrl;

    @Column(name = "preferences", columnDefinition = "text")
    private String preferences;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CoverLetterStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "latest_review_version_id", length = 64)
    private String latestReviewVersionId;

    // 초안 생성에 필요한 필수 컬럼만 채움
    private CoverLetter(String id, String ownerId, CoverLetterStatus status, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static CoverLetter draft(String id, String ownerId, Instant now) {
        return new CoverLetter(id, ownerId, CoverLetterStatus.DRAFT, now);
    }

    public void fillBasicInfo(String title, String companyName, String positionTitle, String jobPostingUrl, Instant now) {
        this.title = title;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.jobPostingUrl = jobPostingUrl;
        this.updatedAt = now;
    }

    public void fillPreferences(String preferences, Instant now) {
        this.preferences = preferences;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void setLatestReviewVersionId(String latestReviewVersionId) {
        this.latestReviewVersionId = latestReviewVersionId;
    }
}
