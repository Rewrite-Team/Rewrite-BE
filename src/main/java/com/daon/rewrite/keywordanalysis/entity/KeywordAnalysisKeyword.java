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
