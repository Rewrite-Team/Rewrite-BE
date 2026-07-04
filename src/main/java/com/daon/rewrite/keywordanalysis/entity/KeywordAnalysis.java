package com.daon.rewrite.keywordanalysis.entity;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "keyword_analyses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_keyword_analyses_cover_letter",
                columnNames = "cover_letter_id"
        )
)
public class KeywordAnalysis {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @Column(name = "source_review_version_id", nullable = false, length = 64)
    private String sourceReviewVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KeywordAnalysisStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private KeywordAnalysis(
            String id,
            CoverLetter coverLetter,
            String sourceReviewVersionId,
            KeywordAnalysisStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.coverLetter = coverLetter;
        this.sourceReviewVersionId = sourceReviewVersionId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static KeywordAnalysis processing(
            String id,
            CoverLetter coverLetter,
            String sourceReviewVersionId,
            Instant now
    ) {
        return new KeywordAnalysis(
                id,
                coverLetter,
                sourceReviewVersionId,
                KeywordAnalysisStatus.PROCESSING,
                now
        );
    }

    public void restart(String sourceReviewVersionId) {
        this.sourceReviewVersionId = sourceReviewVersionId;
        this.status = KeywordAnalysisStatus.PROCESSING;
        this.completedAt = null;
    }
}
