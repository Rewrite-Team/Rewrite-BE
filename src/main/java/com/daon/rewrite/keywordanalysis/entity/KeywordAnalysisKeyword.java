package com.daon.rewrite.keywordanalysis.entity;

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

/**
 * 분석 결과의 키워드 한 항목과 중요도를 보관하며, 재분석 성공 시 전체 목록을 새 결과로 교체한다.
 * 항목 검증·중요도 정렬·상위 개수 제한은 LLM 클라이언트가 수행하고 저장 계층은 전달된 순서를 기록한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "keyword_analysis_keywords",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_keyword_analysis_keywords_order",
                columnNames = {"keyword_analysis_id", "keyword_order"}
        )
)
public class KeywordAnalysisKeyword {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keyword_analysis_id", nullable = false)
    private KeywordAnalysis keywordAnalysis;

    // 중요도 내림차순으로 정렬된 결과 목록에 1부터 부여한 표시 순서다.
    @Column(name = "keyword_order", nullable = false)
    private int keywordOrder;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "importance", nullable = false)
    private int importance;

    private KeywordAnalysisKeyword(
            String id,
            KeywordAnalysis keywordAnalysis,
            int keywordOrder,
            String keyword,
            int importance
    ) {
        this.id = id;
        this.keywordAnalysis = keywordAnalysis;
        this.keywordOrder = keywordOrder;
        this.keyword = keyword;
        this.importance = importance;
    }

    public static KeywordAnalysisKeyword of(
            String id,
            KeywordAnalysis keywordAnalysis,
            int keywordOrder,
            String keyword,
            int importance
    ) {
        return new KeywordAnalysisKeyword(
                id,
                keywordAnalysis,
                keywordOrder,
                keyword,
                importance
        );
    }
}
