package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewVersionRepository extends JpaRepository<ReviewVersion, String> {

    List<ReviewVersion> findByCoverLetterIdOrderByCreatedAtAsc(String coverLetterId);

    Optional<ReviewVersion> findByIdAndCoverLetterId(String id, String coverLetterId);

    Optional<ReviewVersion> findByLlmJobId(String llmJobId);

    long countByCoverLetterId(String coverLetterId);
}
