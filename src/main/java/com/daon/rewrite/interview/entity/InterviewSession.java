package com.daon.rewrite.interview.entity;

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
 * 자기소개서당 하나만 유지하는 면접 세션으로 기존 질문과 대화방의 소속을 묶는다.
 * 상태는 초기 질문 생성 중·활성·실패를 나타내며, ACTIVE 이후 추가 질문·피드백의 실패는 각 Job에서 관리한다.
 * 재첨삭은 기존 세션을 교체하지 않고, 초기 생성 실패 후 재시도도 같은 세션 ID를 재사용한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "interview_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_sessions_cover_letter",
                columnNames = "cover_letter_id"
        )
)
public class InterviewSession {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    // 초기 질문 생성 기준이다. 초기 실패 재시도에는 갱신하고, 추가 질문의 기준은 각 InterviewQuestion에 기록한다.
    @Column(name = "initial_source_review_version_id", nullable = false, length = 64)
    private String initialSourceReviewVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InterviewSessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private InterviewSession(
            String id,
            CoverLetter coverLetter,
            String initialSourceReviewVersionId,
            InterviewSessionStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.coverLetter = coverLetter;
        this.initialSourceReviewVersionId = initialSourceReviewVersionId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static InterviewSession questionGenerating(
            String id,
            CoverLetter coverLetter,
            String initialSourceReviewVersionId,
            Instant now
    ) {
        return new InterviewSession(
                id,
                coverLetter,
                initialSourceReviewVersionId,
                InterviewSessionStatus.QUESTION_GENERATING,
                now
        );
    }

    /** 초기 생성에 실패한 세션만 새 기준 버전으로 다시 시작하며, 준비된 ACTIVE 세션은 재생성하지 않는다. */
    public void restartQuestionGeneration(String sourceReviewVersionId) {
        if (status != InterviewSessionStatus.FAILED) {
            throw new IllegalStateException("FAILED 면접 세션만 질문 생성을 다시 시작할 수 있습니다.");
        }
        this.initialSourceReviewVersionId = sourceReviewVersionId;
        this.status = InterviewSessionStatus.QUESTION_GENERATING;
    }

    public void activate() {
        if (status != InterviewSessionStatus.QUESTION_GENERATING) {
            throw new IllegalStateException("QUESTION_GENERATING 면접 세션만 활성화할 수 있습니다.");
        }
        this.status = InterviewSessionStatus.ACTIVE;
    }

    public void fail() {
        if (status != InterviewSessionStatus.QUESTION_GENERATING) {
            throw new IllegalStateException("QUESTION_GENERATING 면접 세션만 실패 처리할 수 있습니다.");
        }
        this.status = InterviewSessionStatus.FAILED;
    }
}
