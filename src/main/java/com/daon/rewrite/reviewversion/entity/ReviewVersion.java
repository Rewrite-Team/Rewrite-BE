package com.daon.rewrite.reviewversion.entity;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 첨삭 Job을 시작할 때 생성하는 시도 이력이며 실패·취소돼도 보존한다.
 * versionNumber는 1부터 증가하는 번호이며, 응답에서 v0.N 라벨로 표시한다. 상태는 연결된 Job에서 읽는다.
 * 최신 성공 결과인지는 자기소개서의 latestReviewedVersionId로 별도 판단한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "review_versions",
        check = @CheckConstraint(name = "ck_review_versions_version_number_positive", constraint = "version_number > 0"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_versions_cover_letter_version_number",
                columnNames = {"cover_letter_id", "version_number"}
        )
)
public class ReviewVersion {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @Column(name = "version_number", nullable = false)
    private long versionNumber;

    @Column(name = "request_instruction", length = 1000)
    private String requestInstruction;

    // 버전의 상태와 실패 정보가 연결된 Job에 있으므로 버전 조회 시 함께 읽는다.
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "llm_job_id", unique = true)
    private LlmJob llmJob;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ReviewVersion(
            String id,
            CoverLetter coverLetter,
            long versionNumber,
            String requestInstruction,
            LlmJob llmJob,
            Instant createdAt
    ) {
        this.id = id;
        this.coverLetter = coverLetter;
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber는 1 이상이어야 합니다.");
        }
        this.versionNumber = versionNumber;
        this.requestInstruction = requestInstruction;
        this.llmJob = llmJob;
        this.createdAt = createdAt;
    }

    public static ReviewVersion started(
            String id,
            CoverLetter coverLetter,
            long versionNumber,
            String requestInstruction,
            LlmJob llmJob,
            Instant createdAt
    ) {
        return new ReviewVersion(id, coverLetter, versionNumber, requestInstruction, llmJob, createdAt);
    }

    /**
     * 신규 버전의 상태는 연결된 Job을 따른다.
     * Job 연결 없이 저장된 기존 성공 버전은 완료로 읽어 이전 데이터를 계속 조회할 수 있게 한다.
     */
    public LlmJobStatus getStatus() {
        return llmJob == null ? LlmJobStatus.COMPLETED : llmJob.getStatus();
    }
}
