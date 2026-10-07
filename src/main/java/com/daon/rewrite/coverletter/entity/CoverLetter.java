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

/**
 * 사용자가 등록한 원본 기본 정보와 자기소개서의 화면 표시 상태를 보관한다.
 * WRITING에서는 미입력 필드를 허용하며, 제출 이후 원본 편집과 상태 전환의 조건은 각 서비스가 검증한다.
 * 첨삭 시도 이력은 ReviewVersion·LlmJob에 남기고 이 엔티티는 최신 성공 버전의 ID를 유지한다.
 */
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

    // 최초 제출 시각이다. 최초 실패 후 재시도나 재첨삭으로 다시 REVIEWING이 되어도 유지한다.
    @Column(name = "submitted_at")
    private Instant submittedAt;

    // 사용자 조회에서 자기소개서와 하위 리소스를 제외하는 soft delete 기준이다.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 마지막 성공 결과를 가리킨다. 최신 시도가 진행·실패 중이어도 기존 성공 버전은 유지한다.
    @Column(name = "latest_review_version_id", length = 64)
    private String latestReviewedVersionId;

    private CoverLetter(String id, String ownerId, CoverLetterStatus status, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static CoverLetter create(String id, String ownerId, Instant now) {
        return new CoverLetter(id, ownerId, CoverLetterStatus.WRITING, now);
    }

    /** 등록 step의 정규화된 전체 폼으로 교체한다. null도 저장하므로 부분 patch로 사용하지 않는다. */
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

    /** 최초 첨삭과 재첨삭 시작 시 표시 상태를 바꾸고, 처음 제출한 시각만 기록한다. */
    public void startReview(Instant now) {
        this.status = CoverLetterStatus.REVIEWING;
        if (this.submittedAt == null) {
            this.submittedAt = now;
        }
        this.updatedAt = now;
    }

    /** 문항 결과를 확정한 버전으로 최신 성공 참조를 교체하고 결과 화면 상태로 전환한다. */
    public void completeReview(String reviewVersionId, Instant now) {
        this.status = CoverLetterStatus.REVIEWED;
        this.latestReviewedVersionId = reviewVersionId;
        this.updatedAt = now;
    }

    /** 실패 화면 상태로 전환한다. 재첨삭 실패에서도 기존 최신 성공 버전은 계속 조회할 수 있도록 참조를 유지한다. */
    public void failReview(Instant now) {
        this.status = CoverLetterStatus.REVIEW_FAILED;
        this.updatedAt = now;
    }
}
