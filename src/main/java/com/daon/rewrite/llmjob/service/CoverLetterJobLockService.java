package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * worker의 상태 변경에 필요한 CoverLetter와 LlmJob을 항상 같은 순서로 잠근다.
 * Job 생성·자기소개서 변경 경로와 CoverLetter → LlmJob 순서를 맞춰 반대 순서의 잠금 경합을 피한다.
 * 자체 트랜잭션은 시작하지 않으며, 호출자의 쓰기 트랜잭션 동안 두 행의 잠금을 유지한다.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterJobLockService {

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;

    /**
     * 대상만 먼저 조회해 CoverLetter를 잠근 뒤 Job을 잠그고, 실제 잠근 두 엔티티의 연결도 다시 확인한다.
     * 호출자는 트랜잭션 안에서 반환된 엔티티의 검증과 상태 변경을 함께 수행해야 한다.
     * 내부 실행 경로의 Job·대상 누락이나 잘못된 연결은 INTERNAL_ERROR로 처리한다.
     */
    public LockedCoverLetterJob lock(String jobId) {
        // Job 행 잠금을 먼저 잡지 않도록 대상 필드만 조회한다.
        LlmJobRepository.JobTarget target = llmJobRepository.findTargetById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (target.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        CoverLetter coverLetter = coverLetterRepository.findByIdForUpdate(target.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        // 최초 대상 조회와 잠금 조회 사이에도 같은 대상 연결이 유지됐는지 확인한다.
        if (job.getTargetType() != LlmJobTargetType.COVER_LETTER
                || !job.getTargetId().equals(coverLetter.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new LockedCoverLetterJob(coverLetter, job);
    }

    public record LockedCoverLetterJob(CoverLetter coverLetter, LlmJob job) {
    }
}
