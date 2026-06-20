package com.daon.rewrite.llmjob.repository;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface LlmJobRepository extends JpaRepository<LlmJob, String> {

    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
            LlmJobTargetType targetType,
            String targetId,
            Collection<LlmJobStatus> statuses
    );
}
