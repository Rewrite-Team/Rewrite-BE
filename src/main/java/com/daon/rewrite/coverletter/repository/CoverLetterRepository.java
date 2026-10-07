package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

/**
 * 사용자 조회에서는 소유자와 soft delete 조건을 함께 적용하고, 변경 경로에는 CoverLetter 행 잠금을 제공한다.
 * 잠금 메서드는 호출자의 쓰기 트랜잭션에서 사용하며 LlmJob 잠금보다 먼저 호출한다.
 */
public interface CoverLetterRepository extends JpaRepository<CoverLetter, String> {

    Page<CoverLetter> findByOwnerIdAndDeletedAtIsNull(String ownerId, Pageable pageable);

    /** 사용자 SSE의 전체 현재 상태 조회용으로 페이지 구분 없이 활성 자기소개서를 반환한다. */
    List<CoverLetter> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(String ownerId);

    Optional<CoverLetter> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    /** 임시저장·제출·삭제·후속 Job 생성에서 소유권을 확인하며 같은 자기소개서의 변경을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coverLetter
            from CoverLetter coverLetter
            where coverLetter.id = :id
              and coverLetter.ownerId = :ownerId
              and coverLetter.deletedAt is null
            """)
    Optional<CoverLetter> findActiveByIdAndOwnerIdForUpdate(
            @Param("id") String id,
            @Param("ownerId") String ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coverLetter
            from CoverLetter coverLetter
            where coverLetter.id = :id
              and coverLetter.deletedAt is null
            """)
    Optional<CoverLetter> findActiveByIdForUpdate(@Param("id") String id);

    /**
     * 내부 Job 처리에서 삭제 여부를 포함한 실제 대상 상태를 확인하기 위해 삭제된 행도 잠금 조회한다.
     * 사용자 접근 권한을 확인하는 조회에는 소유자·활성 조건이 있는 메서드를 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coverLetter
            from CoverLetter coverLetter
            where coverLetter.id = :id
            """)
    Optional<CoverLetter> findByIdForUpdate(@Param("id") String id);
}
