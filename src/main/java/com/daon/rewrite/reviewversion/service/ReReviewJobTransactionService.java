package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import com.daon.rewrite.reviewversion.client.FirstReviewQuestion;
import com.daon.rewrite.reviewversion.client.FirstReviewRequest;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
class ReReviewJobTransactionService {

    private static final String STARTED_MESSAGE = "재첨삭을 시작합니다.";
    private static final String FAILED_MESSAGE = "LLM 재첨삭에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final Clock clock;

    @Transactional
    public FirstReviewWork start(String jobId) {
        LlmJob job = findReReviewJobForUpdate(jobId);
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = coverLetterRepository.findActiveByIdForUpdate(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED
                || coverLetter.getLatestReviewVersionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        ReviewVersion latestVersion = reviewVersionRepository.findByIdAndCoverLetterId(
                        coverLetter.getLatestReviewVersionId(),
                        coverLetter.getId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(latestVersion.getId());
        if (questionResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new FirstReviewWork(new FirstReviewRequest(
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getJobPostingUrl(),
                coverLetter.getPreferences(),
                job.getRequestInstruction(),
                questionResults.stream()
                        .map(result -> new FirstReviewQuestion(
                                result.getQuestion().getId(),
                                result.getQuestionOrder(),
                                result.getQuestionText(),
                                result.getMaxAnswerLength(),
                                result.getFinalAnswer()
                        ))
                        .toList()
        ));
    }

    @Transactional
    public void fail(String jobId, FirstReviewClientException.Reason reason) {
        LlmJob job = findReReviewJobForUpdate(jobId);
        if (job.getStatus() == LlmJobStatus.COMPLETED || job.getStatus() == LlmJobStatus.FAILED) {
            return;
        }

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                now
        );
    }

    private LlmJob findReReviewJobForUpdate(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (job.getType() != LlmJobType.COVER_LETTER_RE_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private String errorCode(FirstReviewClientException.Reason reason) {
        if (reason == FirstReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(FirstReviewClientException.Reason reason) {
        if (reason == FirstReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }
}
