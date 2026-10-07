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

/**
 * 사용자에게 공개할 Job의 접근 권한을 확인하고, 도메인 서비스의 진행 중 Job 조회를 공통화한다.
 * Job 생성·실행과 같은 요청의 재사용·충돌 판단은 각 도메인 서비스가 담당한다.
 */
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

    /**
     * 자기소개서의 최신 PENDING·PROCESSING Job을 잠금 조회하고, 없으면 null을 반환한다.
     * 호출자는 소유권을 확인하고 같은 트랜잭션에서 CoverLetter를 먼저 잠근 뒤 이 메서드를 호출해야 한다.
     * Job이 없는 경우도 CoverLetter 잠금으로 직렬화하므로 조회부터 새 Job 생성까지 같은 트랜잭션을 유지한다.
     */
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
