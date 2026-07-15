package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, String> {

    Optional<InterviewSession> findByCoverLetterId(String coverLetterId);

    Optional<InterviewSession> findByIdAndCoverLetterOwnerIdAndCoverLetterDeletedAtIsNull(
            String id,
            String ownerId
    );

    @Query("""
            select session.coverLetter.id
            from InterviewSession session
            where session.id = :id
              and session.coverLetter.ownerId = :ownerId
              and session.coverLetter.deletedAt is null
            """)
    Optional<String> findActiveCoverLetterIdByIdAndOwnerId(
            @Param("id") String id,
            @Param("ownerId") String ownerId
    );
}
