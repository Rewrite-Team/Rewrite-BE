package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.RunningCoverLetterJobFinder;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 최신 성공 버전을 입력으로 하는 재첨삭 요청을 처리한다.
 * 자기소개서 잠금 안에서 진행 Job을 확인해 중복 요청과 다른 AI Job과의 충돌을 판단한다.
 * 외부 LLM 호출은 여기서 수행하지 않고 커밋 후 Job 이벤트로 실행한다.
 */
@Service
@RequiredArgsConstructor
public class ReReviewRequestService {

    private static final int MAX_REQUEST_INSTRUCTION_LENGTH = 1000;
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final String REVIEW_VERSION_ID_PREFIX = "rv";

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final LlmJobRepository llmJobRepository;
    private final RunningCoverLetterJobFinder runningJobFinder;
    private final ReviewRetrySnapshotService retrySnapshotService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 요청 시점의 최신 성공 버전과 문항별 finalAnswer를 새 Job의 입력으로 고정한다.
     * Job·다음 버전·입력 스냅샷을 함께 저장하고 자기소개서를 REVIEWING으로 전환한다.
     * 같은 재첨삭이 진행 중이면 기존 Job을 반환하며 새 요구사항은 반영하지 않는다.
     * 같은 기준 버전의 직전 재첨삭이 일부 성공 후 실패했다면 새 Job·버전을 만들되, 입력이 같은 성공 문항 결과를 이어받아
     * 나머지 문항만 다시 실행한다. 요구사항을 비우면 직전 값을 이어받고, 다른 값이면 전체를 새로 실행한다.
     */
    @Transactional
    public RequestReReviewResult requestMyReReview(String coverLetterId, String requestInstruction) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        LlmJob runningJob = runningJobFinder.find(coverLetter.getId());
        // 새 요구사항 검증보다 먼저 기존 작업을 반환해 중복 요청으로 실행 중인 입력이 바뀌지 않게 한다.
        if (runningJob != null && runningJob.getType() == LlmJobType.COVER_LETTER_RE_REVIEW) {
            return new RequestReReviewResult(coverLetter, runningJob);
        }
        if (runningJob != null) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        List<ReviewVersionQuestionResult> latestResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(coverLetter.getLatestReviewedVersionId());
        if (latestResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        String normalizedInstruction = validateAndNormalizeRequestInstruction(requestInstruction);
        // 자기소개서 잠금 안에서 실패·취소를 포함한 최대 번호 다음으로 새 버전을 생성한다.
        long nextVersionNumber = Math.incrementExact(
                reviewVersionRepository.findMaxVersionNumberByCoverLetterId(coverLetter.getId()));
        // 같은 기준 버전의 직전 재첨삭이 일부 성공 후 실패했다면 입력이 같은 성공 문항을 이어받는다.
        List<ReviewRetrySnapshotService.QuestionInput> inputs = latestResults.stream()
                .map(result -> new ReviewRetrySnapshotService.QuestionInput(result.getQuestion(), result.getFinalAnswer()))
                .toList();
        ReviewRetrySnapshotService.RetryPlan retryPlan = retrySnapshotService.plan(
                coverLetter.getId(),
                nextVersionNumber,
                LlmJobType.COVER_LETTER_RE_REVIEW,
                coverLetter.getLatestReviewedVersionId(),
                normalizedInstruction,
                inputs
        );
        Instant now = Instant.now(clock);
        LlmJob job = llmJobRepository.save(LlmJob.pendingReReview(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                retryPlan.requestInstruction(),
                coverLetter.getLatestReviewedVersionId(),
                now,
                latestResults.size()
        ));
        reviewVersionRepository.save(ReviewVersion.started(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                nextVersionNumber,
                retryPlan.requestInstruction(),
                job,
                now
        ));
        // 이후 최종 작성본을 수정해도 실행 대기 중인 재첨삭의 답변 입력은 이 스냅샷을 사용한다.
        retrySnapshotService.stage(job, retryPlan, inputs);
        coverLetter.startReview(now);
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new RequestReReviewResult(coverLetter, job);
    }

    // 선택 요구사항은 공백 제거 후 비어 있으면 없음으로 처리하고, 있으면 code point 기준 1000자로 제한한다.
    private String validateAndNormalizeRequestInstruction(String requestInstruction) {
        String normalized = normalize(requestInstruction);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (countCodePoints(normalized) > MAX_REQUEST_INSTRUCTION_LENGTH) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new ErrorResponse.ErrorDetail(
                            "requestInstruction",
                            "재첨삭 요구사항은 최대 1000자까지 입력할 수 있습니다."
                    ))
            );
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.strip();
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
