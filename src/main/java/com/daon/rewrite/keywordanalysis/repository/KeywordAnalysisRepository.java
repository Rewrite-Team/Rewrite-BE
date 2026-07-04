package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KeywordAnalysisRepository extends JpaRepository<KeywordAnalysis, String> {

    Optional<KeywordAnalysis> findByCoverLetterId(String coverLetterId);
}
