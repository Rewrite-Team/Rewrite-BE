package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewThread;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 질문 목록의 thread를 일괄 조회하고, 대화 진입에는 부모 자기소개서의 소유권·soft delete 조건을 적용한다.
 * 질문 참조를 함께 읽어 질문 ID와 thread ID의 대응을 구성한다.
 */
public interface InterviewThreadRepository extends JpaRepository<InterviewThread, String> {

    @EntityGraph(attributePaths = "interviewQuestion")
    List<InterviewThread> findByInterviewSessionId(String interviewSessionId);

    @EntityGraph(attributePaths = "interviewQuestion")
    List<InterviewThread> findByInterviewQuestionIdIn(List<String> interviewQuestionIds);

    @Query("""
            select thread
            from InterviewThread thread
            join thread.interviewSession session
            join session.coverLetter coverLetter
            where thread.id = :id
              and coverLetter.ownerId = :ownerId
              and coverLetter.deletedAt is null
            """)
    Optional<InterviewThread> findActiveByIdAndOwnerId(
            @Param("id") String id,
            @Param("ownerId") String ownerId
    );
}
