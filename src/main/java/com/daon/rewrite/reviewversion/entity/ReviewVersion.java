package com.daon.rewrite.reviewversion.entity;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "review_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_versions_cover_letter_version",
                columnNames = {"cover_letter_id", "version"}
        )
)
public class ReviewVersion {

    private static final String FIRST_VERSION = "v0.1";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "request_instruction", length = 1000)
    private String requestInstruction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ReviewVersion(
            String id,
            CoverLetter coverLetter,
            String version,
            String requestInstruction,
            Instant createdAt
    ) {
        this.id = id;
        this.coverLetter = coverLetter;
        this.version = version;
        this.requestInstruction = requestInstruction;
        this.createdAt = createdAt;
    }

    public static ReviewVersion first(String id, CoverLetter coverLetter, Instant createdAt) {
        return new ReviewVersion(id, coverLetter, FIRST_VERSION, null, createdAt);
    }
}
