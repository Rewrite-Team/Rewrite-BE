package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, String> {

    List<InterviewMessage> findByThreadIdOrderByCreatedAtAscIdAsc(String threadId);
}
