package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 첨삭 시도 이력을 자기소개서나 연결된 Job 기준으로 조회한다.
 * 성공 여부로 이력을 거르지 않으며 최신 성공 버전 참조는 자기소개서가 별도로 관리한다.
 */
public interface ReviewVersionRepository extends JpaRepository<ReviewVersion, String> {

    List<ReviewVersion> findByCoverLetterIdOrderByCreatedAtAsc(String coverLetterId);

    Optional<ReviewVersion> findByIdAndCoverLetterId(String id, String coverLetterId);

    Optional<ReviewVersion> findByLlmJobId(String llmJobId);

    // 실패·취소를 포함한 전체 버전 수를 세어 새 시도의 다음 표시 번호를 정할 때 사용한다.
    long countByCoverLetterId(String coverLetterId);
}
