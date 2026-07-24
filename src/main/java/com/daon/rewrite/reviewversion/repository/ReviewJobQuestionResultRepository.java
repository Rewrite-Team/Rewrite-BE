package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewJobQuestionResultRepository extends JpaRepository<ReviewJobQuestionResult, String> {

    List<ReviewJobQuestionResult> findByLlmJobIdOrderByQuestionOrderAsc(String llmJobId);

    Optional<ReviewJobQuestionResult> findByLlmJobIdAndQuestionId(String llmJobId, String questionId);

    @EntityGraph(attributePaths = "question")
    List<ReviewJobQuestionResult> findByLlmJobIdAndStatusOrderByQuestionOrderAsc(
            String llmJobId,
            ReviewJobQuestionResultStatus status
    );
}
