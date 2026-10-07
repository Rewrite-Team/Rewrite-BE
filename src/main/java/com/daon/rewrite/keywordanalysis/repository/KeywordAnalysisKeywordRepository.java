package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 완료 키워드를 저장된 표시 순서로 조회하고, 재분석 성공 트랜잭션에서 이전 목록을 제거한다.
 * 결과 루트의 상태에 따른 노출 여부는 조회 서비스가 판단한다.
 */
public interface KeywordAnalysisKeywordRepository extends JpaRepository<KeywordAnalysisKeyword, String> {

    List<KeywordAnalysisKeyword> findByKeywordAnalysisIdOrderByKeywordOrderAsc(String keywordAnalysisId);

    void deleteByKeywordAnalysisId(String keywordAnalysisId);
}
