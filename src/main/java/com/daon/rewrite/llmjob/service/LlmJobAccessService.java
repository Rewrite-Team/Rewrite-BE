package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자에게 공개할 Job의 접근 권한을 확인하고 현재 사용자의 Job을 조회한다.
 */
@Service
@RequiredArgsConstructor
public class LlmJobAccessService {

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 현재 사용자의 삭제되지 않은 자기소개서에 연결된 Job만 반환한다.
     * Job 없음·지원하지 않는 대상·비소유·삭제를 모두 NOT_FOUND로 처리해 다른 사용자의 리소스 존재를 노출하지 않는다.
     */
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
}
