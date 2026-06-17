package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, String> {

    Page<CoverLetter> findByOwnerIdAndDeletedAtIsNull(String ownerId, Pageable pageable);

    Page<CoverLetter> findByOwnerIdAndStatusAndDeletedAtIsNull(
            String ownerId,
            CoverLetterStatus status,
            Pageable pageable
    );

    Optional<CoverLetter> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);
}
