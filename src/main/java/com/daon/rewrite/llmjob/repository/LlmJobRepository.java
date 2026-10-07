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

/**
 * 대상·작업 종류·입력 참조를 기준으로 Job을 찾고 상태 변경에 필요한 행 잠금을 제공한다.
 * 최신 조회는 생성 시각과 ID의 내림차순을 사용하며, 잠금 조회는 호출자의 쓰기 트랜잭션 안에서 수행한다.
 */
public interface LlmJobRepository extends JpaRepository<LlmJob, String> {

    /** Job을 잠그기 전에 대상 CoverLetter를 결정하는 데 필요한 필드만 읽는 projection이다. */
    interface JobTarget {
        String getTargetId();

        LlmJobTargetType getTargetType();
    }

    /**
     * CoverLetter → LlmJob 잠금 순서를 지키기 위한 대상 조회다. 이 조회 자체는 Job 행을 잠그지 않는다.
     * CoverLetterJobLockService가 대상 잠금 이후 실제 Job을 잠그고 연결을 재확인한다.
     */
    @Query("select job.targetId as targetId, job.targetType as targetType from LlmJob job where job.id = :id")
    Optional<JobTarget> findTargetById(@Param("id") String id);

    /**
     * 조건에 맞는 최신 Job 행을 잠근다. 진행 중 Job 확인에는 PENDING·PROCESSING을 전달한다.
     * 결과가 없을 때의 동시 생성 배제는 호출자가 먼저 획득한 CoverLetter 잠금으로 처리한다.
     */
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

    /** 입력 USER 메시지별 최신 피드백 Job을 찾아 진행·실패 상태 복구에 사용한다. */
    Optional<LlmJob> findFirstByTypeAndRequestRefTypeAndRequestRefIdOrderByCreatedAtDescIdDesc(
            LlmJobType type,
            LlmJobRequestRefType requestRefType,
            String requestRefId
    );

    /**
     * 상태·진행률·결과를 변경할 Job을 쓰기 잠금으로 조회한다.
     * CoverLetter도 변경하는 경로는 CoverLetterJobLockService를 통해 대상 행을 먼저 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from LlmJob job where job.id = :id")
    Optional<LlmJob> findByIdForUpdate(@Param("id") String id);
}
