package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Job별 입력 스냅샷과 문항 부분 결과를 문항 순서대로 읽는 저장 경계다.
 * 상세·SSE 복구에서는 현재 Job의 결과를 읽고, 완료 확정에서는 전체 문항의 성공 여부를 확인한다.
 */
public interface ReviewJobQuestionResultRepository extends JpaRepository<ReviewJobQuestionResult, String> {

    List<ReviewJobQuestionResult> findByLlmJobIdOrderByQuestionOrderAsc(String llmJobId);

    Optional<ReviewJobQuestionResult> findByLlmJobIdAndQuestionId(String llmJobId, String questionId);

    // 완료 문항 SSE에 원본 questionId도 포함하므로 연결된 문항을 함께 읽는다.
    @EntityGraph(attributePaths = "question")
    List<ReviewJobQuestionResult> findByLlmJobIdAndStatusOrderByQuestionOrderAsc(
            String llmJobId,
            ReviewJobQuestionResultStatus status
    );
}
