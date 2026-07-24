package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, String> {

    List<InterviewMessage> findByThreadIdOrderByCreatedAtAscIdAsc(String threadId);

    Optional<InterviewMessage> findFirstByThreadIdAndRoleOrderByCreatedAtDescIdDesc(
            String threadId,
            InterviewMessageRole role
    );
}
