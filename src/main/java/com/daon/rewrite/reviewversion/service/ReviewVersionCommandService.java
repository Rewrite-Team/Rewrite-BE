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
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.LlmJobService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 최신 성공 버전의 최종 작성본 저장과 그 버전을 입력으로 하는 재첨삭 요청을 처리한다.
 * 자기소개서 잠금 안에서 버전·진행 Job을 확인해 저장과 재첨삭 입력 확정의 순서를 맞춘다.
 * 외부 LLM 호출은 여기서 수행하지 않고 커밋 후 Job 이벤트로 실행한다.
 */
@Service
@RequiredArgsConstructor
public class ReviewVersionCommandService {

    private static final int MAX_FINAL_ANSWER_LENGTH = 5000;
    private static final int MAX_REQUEST_INSTRUCTION_LENGTH = 1000;
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final String REVIEW_VERSION_ID_PREFIX = "rv";

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final LlmJobRepository llmJobRepository;
    private final LlmJobService llmJobService;
    private final ReviewRetrySnapshotService retrySnapshotService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 요청한 버전이 저장 시점에도 최신 성공 버전인지 확인하고 모든 문항의 최종 작성본을 함께 갱신한다.
     * 전체 입력의 누락·중복·소속과 문자열을 먼저 검증해 부분 저장을 막으며 새 버전도 만들지 않는다.
     */
    @Transactional
    public void saveMyFinalAnswers(
            String coverLetterId,
            String versionId,
            List<SaveFinalAnswerInput> inputs
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ReviewVersion reviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!reviewVersion.getId().equals(coverLetter.getLatestReviewedVersionId())
                || reviewVersion.getStatus() != LlmJobStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REVIEW_VERSION_NOT_LATEST);
        }

        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());
        Map<String, String> normalizedAnswers = validateAndNormalize(questionResults, inputs);

        for (ReviewVersionQuestionResult questionResult : questionResults) {
            questionResult.updateFinalAnswer(normalizedAnswers.get(questionResult.getId()));
        }

    }

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

        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
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

    /**
     * 입력 배열이 해당 버전의 모든 문항 결과를 정확히 한 번씩 포함하는지 확인한다.
     * 답변은 앞뒤 공백 제거 후 1~5000 code point로 검증하며 문항별 오류를 모아 예외에 담는다.
     */
    private Map<String, String> validateAndNormalize(
            List<ReviewVersionQuestionResult> questionResults,
            List<SaveFinalAnswerInput> inputs
    ) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        if (questionResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (inputs == null || inputs.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers",
                    "최종 작성본은 모든 문항을 포함해야 합니다."
            ));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        Set<String> expectedIds = new HashSet<>();
        for (ReviewVersionQuestionResult questionResult : questionResults) {
            expectedIds.add(questionResult.getId());
        }

        Set<String> seenIds = new HashSet<>();
        Map<String, String> normalizedAnswers = new HashMap<>();
        for (int index = 0; index < inputs.size(); index++) {
            SaveFinalAnswerInput input = inputs.get(index);
            if (input == null) {
                details.add(new ErrorResponse.ErrorDetail(
                        "answers[" + index + "]",
                        "최종 작성본 정보를 입력해야 합니다."
                ));
                continue;
            }

            boolean validId = validateQuestionResultId(index, input.questionResultId(), expectedIds, seenIds, details);
            String normalizedFinalAnswer = validateFinalAnswer(index, input.finalAnswer(), details);
            if (validId) {
                seenIds.add(input.questionResultId());
                if (normalizedFinalAnswer != null) {
                    normalizedAnswers.put(input.questionResultId(), normalizedFinalAnswer);
                }
            }
        }

        if (normalizedAnswers.size() != questionResults.size()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers",
                    "첨삭 버전에 포함된 모든 문항의 최종 작성본을 입력해야 합니다."
            ));
        }

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }
        return normalizedAnswers;
    }

    private boolean validateQuestionResultId(
            int index,
            String questionResultId,
            Set<String> expectedIds,
            Set<String> seenIds,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (questionResultId == null || questionResultId.isBlank()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "문항 결과 ID는 필수입니다."
            ));
            return false;
        }
        if (!expectedIds.contains(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "첨삭 버전에 포함되지 않은 문항 결과입니다."
            ));
            return false;
        }
        if (seenIds.contains(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "중복된 문항 결과입니다."
            ));
            return false;
        }
        return true;
    }

    private String validateFinalAnswer(
            int index,
            String finalAnswer,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (finalAnswer == null) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본을 입력해야 합니다."
            ));
            return null;
        }

        String normalized = finalAnswer.strip();
        if (normalized.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본을 입력해야 합니다."
            ));
            return null;
        }
        if (countCodePoints(normalized) > MAX_FINAL_ANSWER_LENGTH) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본은 최대 5000자까지 입력할 수 있습니다."
            ));
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
