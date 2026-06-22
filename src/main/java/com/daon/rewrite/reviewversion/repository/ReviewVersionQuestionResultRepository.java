package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewVersionQuestionResultRepository extends JpaRepository<ReviewVersionQuestionResult, String> {

    List<ReviewVersionQuestionResult> findByReviewVersionIdOrderByQuestionOrderAsc(String reviewVersionId);
}
