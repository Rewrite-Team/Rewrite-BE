package com.daon.rewrite.keywordanalysis.repository;

import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 자기소개서별 단일 분석 루트를 조회한다. 재분석도 같은 행을 찾아 상태와 기준 버전을 갱신한다.
 * 소유권·soft delete 확인과 실행 시작의 잠금은 상위 서비스의 CoverLetter 조회가 담당한다.
 */
public interface KeywordAnalysisRepository extends JpaRepository<KeywordAnalysis, String> {

    Optional<KeywordAnalysis> findByCoverLetterId(String coverLetterId);
}
