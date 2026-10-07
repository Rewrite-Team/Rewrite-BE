package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 성공 확정된 버전의 전체 문항 결과를 입력 순서대로 조회한다.
 * 버전 상세·최종본 일괄 저장과 후속 재첨삭·키워드·면접의 입력 조회에 사용한다.
 */
public interface ReviewVersionQuestionResultRepository extends JpaRepository<ReviewVersionQuestionResult, String> {

    List<ReviewVersionQuestionResult> findByReviewVersionIdOrderByQuestionOrderAsc(String reviewVersionId);
}
