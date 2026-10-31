package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 도메인 서비스의 진행 중 Job 조회를 공통화한다.
 * Job 생성·실행과 같은 요청의 재사용·충돌 판단은 각 도메인 서비스가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class RunningCoverLetterJobFinder {

    private static final List<LlmJobStatus> RUNNING_STATUSES = List.of(
            LlmJobStatus.PENDING,
            LlmJobStatus.PROCESSING
    );

    private final LlmJobRepository llmJobRepository;

    /**
     * 자기소개서의 최신 PENDING·PROCESSING Job을 잠금 조회하고, 없으면 null을 반환한다.
     * 호출자는 소유권을 확인하고 같은 트랜잭션에서 CoverLetter를 먼저 잠근 뒤 이 메서드를 호출해야 한다.
     * Job이 없는 경우도 CoverLetter 잠금으로 직렬화하므로 조회부터 새 Job 생성까지 같은 트랜잭션을 유지한다.
     */
    public LlmJob find(String coverLetterId) {
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        RUNNING_STATUSES
                )
                .orElse(null);
    }
}
