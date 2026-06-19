package com.daon.rewrite.llmjob.repository;

import com.daon.rewrite.llmjob.entity.LlmJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmJobRepository extends JpaRepository<LlmJob, String> {
}
