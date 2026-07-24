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
        LlmJobRepository.JobTarget target = llmJobRepository.findTargetById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (target.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        CoverLetter coverLetter = coverLetterRepository.findByIdForUpdate(target.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (job.getTargetType() != LlmJobTargetType.COVER_LETTER
                || !job.getTargetId().equals(coverLetter.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new LockedCoverLetterJob(coverLetter, job);
    }

    public record LockedCoverLetterJob(CoverLetter coverLetter, LlmJob job) {
    }
}
