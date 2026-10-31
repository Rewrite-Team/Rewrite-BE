package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 지정한 번호 바로 앞의 시도를 반환한다. 실패 후 재시도에서 직전 시도를 찾는 데 사용한다. */
    Optional<ReviewVersion> findFirstByCoverLetterIdAndVersionNumberLessThanOrderByVersionNumberDesc(
            String coverLetterId,
            long versionNumber
    );

    /** 다음 번호 생성과 최신 시도 판별에 사용하며, 아직 시도가 없으면 0을 반환한다. */
    @Query("""
            select coalesce(max(reviewVersion.versionNumber), 0)
            from ReviewVersion reviewVersion
            where reviewVersion.coverLetter.id = :coverLetterId
            """)
    long findMaxVersionNumberByCoverLetterId(@Param("coverLetterId") String coverLetterId);

    long countByCoverLetterId(String coverLetterId);
}
