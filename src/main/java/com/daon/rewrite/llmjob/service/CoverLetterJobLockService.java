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

@Service
@RequiredArgsConstructor
public class CoverLetterJobLockService {

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;

    public LockedCoverLetterJob lock(String jobId) {
        // Job의 targetId/targetType만 먼저 조회
        LlmJobRepository.JobTarget target = llmJobRepository.findTargetById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        // 자소서 첨삭 작업의 Job인지 확인
        if (target.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // CoverLetter 행 PESSIMISTIC_WRITE 잠금
        CoverLetter coverLetter = coverLetterRepository.findByIdForUpdate(target.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        // LlmJob PESSIMISTIC_WRITE 잠금
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        // 실제 잠긴 Job도 자기소개서를 대상으로 하는지 확인
        if (job.getTargetType() != LlmJobTargetType.COVER_LETTER
                //Job이 가리키는 대상 ID와 잠근 자기소개서 ID가 같은지 확인
                || !job.getTargetId().equals(coverLetter.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 잠긴 엔티티를 반환
        return new LockedCoverLetterJob(coverLetter, job);
    }

    public record LockedCoverLetterJob(CoverLetter coverLetter, LlmJob job) {
    }
}
