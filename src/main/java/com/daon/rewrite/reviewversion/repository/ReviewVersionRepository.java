package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewVersionRepository extends JpaRepository<ReviewVersion, String> {
}
