package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, String> {

    Optional<InterviewSession> findByCoverLetterId(String coverLetterId);
}
