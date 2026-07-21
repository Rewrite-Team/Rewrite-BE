package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewThread;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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
