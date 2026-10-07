package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 자기소개서별 단일 세션을 찾고 사용자 조회에는 부모 자기소개서의 소유권·soft delete 조건을 적용한다.
 * 세션 시작·추가 생성의 동시 실행 배제는 상위 서비스가 획득한 CoverLetter 잠금으로 처리한다.
 */
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, String> {

    Optional<InterviewSession> findByCoverLetterId(String coverLetterId);

    Optional<InterviewSession> findByIdAndCoverLetterOwnerIdAndCoverLetterDeletedAtIsNull(
            String id,
            String ownerId
    );

    /** 추가 생성 요청에서 세션 전체를 읽기 전에 먼저 잠글 부모 CoverLetter ID를 찾는다. */
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
