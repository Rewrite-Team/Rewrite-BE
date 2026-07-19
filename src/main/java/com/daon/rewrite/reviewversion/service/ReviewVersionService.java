package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
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
public class ReviewVersionService {

    private static final String REVIEW_VERSION_ID_PREFIX = "rv";
    private static final String QUESTION_RESULT_ID_PREFIX = "rvqr";
    private static final String COMPLETED_MESSAGE = "첨삭이 완료되었습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final ReviewJobQuestionResultRepository jobQuestionResultRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public CompleteReviewResult completeFirstReview(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        validateFirstReviewJob(job);

        if (job.getStatus() == LlmJobStatus.COMPLETED) {
            return findCompletedResult(job);
        }
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        CoverLetter coverLetter = coverLetterRepository.findActiveByIdForUpdate(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        List<ReviewJobQuestionResult> stagedResults = findCompletedStagedResults(job);

        Instant now = Instant.now(clock);
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                now
        ));

        List<ReviewVersionQuestionResult> questionResults = stagedResults.stream()
                .map(stagedResult -> ReviewVersionQuestionResult.createFromSnapshot(
                    idGenerator.generate(QUESTION_RESULT_ID_PREFIX),
                    reviewVersion,
                    stagedResult.getQuestion(),
                    stagedResult.getInputAnswer(),
                    stagedResult.getAiReport(),
                    stagedResult.getRewrittenAnswer()
                ))
                .toList();
        questionResults = questionResultRepository.saveAll(questionResults);

        coverLetter.completeReview(reviewVersion.getId(), now);
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                LlmJobResultRefType.REVIEW_VERSION,
                reviewVersion.getId(),
                now
        );

        return new CompleteReviewResult(reviewVersion, questionResults);
    }

    @Transactional
    public CompleteReviewResult completeReReview(String jobId) {
        // 재첨삭 Job 완료 처리는 동일 Job의 중복 완료 요청을 막기 위해 row lock을 잡고 진행한다.
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        validateReReviewJob(job);

        if (job.getStatus() == LlmJobStatus.COMPLETED) {
            return findCompletedResult(job);
        }
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 재첨삭은 이미 최초 첨삭이 완료된 자기소개서에서만 가능하다.
        CoverLetter coverLetter = coverLetterRepository.findActiveByIdForUpdate(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED
                || coverLetter.getLatestReviewVersionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        if (job.getRequestRefType() != LlmJobRequestRefType.REVIEW_VERSION
                || job.getRequestRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        reviewVersionRepository.findByIdAndCoverLetterId(
                        job.getRequestRefId(),
                        coverLetter.getId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewJobQuestionResult> stagedResults = findCompletedStagedResults(job);

        Instant now = Instant.now(clock);
        // 성공한 재첨삭만 새 ReviewVersion으로 확정한다. 실패한 Job은 기존 최신 버전을 유지한다.
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.reReview(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                nextVersion(coverLetter.getId()),
                job.getRequestInstruction(),
                now
        ));

        List<ReviewVersionQuestionResult> questionResults = stagedResults.stream()
                .map(stagedResult -> ReviewVersionQuestionResult.createFromSnapshot(
                    idGenerator.generate(QUESTION_RESULT_ID_PREFIX),
                    reviewVersion,
                    stagedResult.getQuestion(),
                    stagedResult.getInputAnswer(),
                    stagedResult.getAiReport(),
                    stagedResult.getRewrittenAnswer()
                ))
                .toList();
        questionResults = questionResultRepository.saveAll(questionResults);

        coverLetter.completeReview(reviewVersion.getId(), now);
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                LlmJobResultRefType.REVIEW_VERSION,
                reviewVersion.getId(),
                now
        );

        return new CompleteReviewResult(reviewVersion, questionResults);
    }

    private void validateFirstReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateReReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_RE_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private CompleteReviewResult findCompletedResult(LlmJob job) {
        if (job.getResultRefType() != LlmJobResultRefType.REVIEW_VERSION
                || job.getResultRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        ReviewVersion reviewVersion = reviewVersionRepository.findById(job.getResultRefId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());
        return new CompleteReviewResult(reviewVersion, questionResults);
    }

    private List<ReviewJobQuestionResult> findCompletedStagedResults(LlmJob job) {
        List<ReviewJobQuestionResult> stagedResults = jobQuestionResultRepository
                .findByLlmJobIdOrderByQuestionOrderAsc(job.getId());
        if (stagedResults.size() != job.getProgressTotal()
                || stagedResults.stream().anyMatch(
                result -> result.getStatus() != ReviewJobQuestionResultStatus.COMPLETED
        )) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return stagedResults;
    }

    private String nextVersion(String coverLetterId) {
        long nextPatch = reviewVersionRepository.countByCoverLetterId(coverLetterId) + 1;
        return "v0." + nextPatch;
    }

}
