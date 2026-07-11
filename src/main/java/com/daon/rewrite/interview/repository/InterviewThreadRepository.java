package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewThread;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewThreadRepository extends JpaRepository<InterviewThread, String> {

    @EntityGraph(attributePaths = "interviewQuestion")
    List<InterviewThread> findByInterviewSessionId(String interviewSessionId);
}
