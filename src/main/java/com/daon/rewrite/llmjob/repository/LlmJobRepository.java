package com.daon.rewrite.llmjob.repository;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

public interface LlmJobRepository extends JpaRepository<LlmJob, String> {

    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
            LlmJobTargetType targetType,
            String targetId,
            Collection<LlmJobStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from LlmJob job where job.id = :id")
    Optional<LlmJob> findByIdForUpdate(@Param("id") String id);
}
