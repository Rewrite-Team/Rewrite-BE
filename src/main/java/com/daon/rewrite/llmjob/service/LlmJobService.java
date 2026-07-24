package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmJobService {

    private static final List<LlmJobStatus> RUNNING_STATUSES = List.of(
            LlmJobStatus.PENDING,
            LlmJobStatus.PROCESSING
    );

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public LlmJob findMyJob(String jobId) {
        LlmJob job = llmJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        CurrentUser currentUser = currentUserProvider.currentUser();
        coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(job.getTargetId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return job;
    }

    public LlmJob findRunningCoverLetterJob(String coverLetterId) {
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        RUNNING_STATUSES
                )
                .orElse(null);
    }
}
