package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, String> {

    List<InterviewQuestion> findByInterviewSessionIdOrderByQuestionOrderAsc(String interviewSessionId);

    List<InterviewQuestion> findByInterviewSessionIdAndQuestionOrderLessThanOrderByQuestionOrderDesc(
            String interviewSessionId,
            int questionOrder,
            Pageable pageable
    );
}
