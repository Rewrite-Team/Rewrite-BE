package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeywordAnalysisKeywordRepository extends JpaRepository<KeywordAnalysisKeyword, String> {

    List<KeywordAnalysisKeyword> findByKeywordAnalysisIdOrderByKeywordOrderAsc(String keywordAnalysisId);
}
