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

/**
 * 자기소개서별 하나만 유지하는 키워드 분석 루트로, 현재 실행의 기준 첨삭 버전과 결과 상태를 기록한다.
 * 재분석·실패 후 수동 재시도에도 같은 행을 재사용하고 실행 이력은 LlmJob으로 구분한다.
 * 재첨삭 완료만으로 기존 분석을 바꾸지 않으며, 사용자가 분석을 다시 요청할 때 기준 버전을 갱신한다.
 */
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

    // 분석 요청 시 선택한 최신 성공 버전이다. 새 실행을 시작할 때 갱신하며 현재 최신 버전과 다를 수 있다.
    @Column(name = "source_review_version_id", nullable = false, length = 64)
    private String sourceReviewVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KeywordAnalysisStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 성공·실패 시각이며, 같은 리소스로 다시 실행할 때 null로 초기화한다.
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

    /**
     * 새 실행의 기준 버전과 PROCESSING 상태를 설정하고 이전 종료 시각을 비운다.
     * 기존 키워드 행은 성공 처리에서 교체하며, 그전까지 조회 서비스가 상태에 따라 노출 여부를 결정한다.
     */
    public void restart(String sourceReviewVersionId) {
        this.sourceReviewVersionId = sourceReviewVersionId;
        this.status = KeywordAnalysisStatus.PROCESSING;
        this.completedAt = null;
    }

    public void complete(Instant completedAt) {
        this.status = KeywordAnalysisStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void fail(Instant completedAt) {
        this.status = KeywordAnalysisStatus.FAILED;
        this.completedAt = completedAt;
    }
}
