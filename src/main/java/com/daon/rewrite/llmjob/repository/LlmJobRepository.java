package com.daon.rewrite.llmjob.repository;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

public interface LlmJobRepository extends JpaRepository<LlmJob, String> {

    // 조회 결과 중 필요한 두 필드만 받을 수 있도록 만든 인터페이스 기반 프로젝션
    // Spring Data JPA가 조회결과의 alias를 getter 이름과 연결해주기에 별도의 구현 클래스를 작성하지 않아도 Spring Data 가 런타임에 JobTarget 구현 객체를 만들어 준다.
    interface JobTarget {
        String getTargetId();

        LlmJobTargetType getTargetType();
    }

    // LlmJob 전체 엔티티를 조회하지 않고 특정 작업의 targetId와 targetType만 가져오며, alias를 지정하여 프로젝션과 연결
    // 엔티티 전체가 아닌 필요한 필드만 가져오기에 가볍다
    @Query("select job.targetId as targetId, job.targetType as targetType from LlmJob job where job.id = :id")
    Optional<JobTarget> findTargetById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDescIdDesc(
            LlmJobTargetType targetType,
            String targetId,
            Collection<LlmJobStatus> statuses
    );

    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndTypeInAndStatusInOrderByCreatedAtDescIdDesc(
            LlmJobTargetType targetType,
            String targetId,
            Collection<LlmJobType> types,
            Collection<LlmJobStatus> statuses
    );

    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndTypeOrderByCreatedAtDescIdDesc(
            LlmJobTargetType targetType,
            String targetId,
            LlmJobType type
    );

    Optional<LlmJob> findFirstByTargetTypeAndTargetIdAndTypeInOrderByCreatedAtDescIdDesc(
            LlmJobTargetType targetType,
            String targetId,
            Collection<LlmJobType> types
    );

    Optional<LlmJob> findFirstByTypeAndRequestRefTypeAndRequestRefIdOrderByCreatedAtDescIdDesc(
            LlmJobType type,
            LlmJobRequestRefType requestRefType,
            String requestRefId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from LlmJob job where job.id = :id")
    Optional<LlmJob> findByIdForUpdate(@Param("id") String id);
}
