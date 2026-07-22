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

public interface CoverLetterRepository extends JpaRepository<CoverLetter, String> {

    Page<CoverLetter> findByOwnerIdAndDeletedAtIsNull(String ownerId, Pageable pageable);

    Optional<CoverLetter> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

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
}
