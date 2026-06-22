package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewVersionService {

    private static final String REVIEW_VERSION_ID_PREFIX = "rv";
    private static final String QUESTION_RESULT_ID_PREFIX = "rvqr";
    private static final String COMPLETED_MESSAGE = "첨삭이 완료되었습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterQuestionRepository questionRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public CompleteFirstReviewResult completeFirstReview(
            String jobId,
            List<ReviewQuestionResultInput> inputs
    ) {
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

        List<CoverLetterQuestion> questions = questionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        List<NormalizedReviewResult> normalizedResults = validateAndNormalize(questions, inputs);

        Instant now = Instant.now(clock);
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                now
        ));

        List<ReviewVersionQuestionResult> questionResults = new ArrayList<>();
        for (NormalizedReviewResult normalizedResult : normalizedResults) {
            questionResults.add(ReviewVersionQuestionResult.create(
                    idGenerator.generate(QUESTION_RESULT_ID_PREFIX),
                    reviewVersion,
                    normalizedResult.question(),
                    normalizedResult.aiReport(),
                    normalizedResult.rewrittenAnswer()
            ));
        }
        questionResults = questionResultRepository.saveAll(questionResults);

        coverLetter.completeReview(reviewVersion.getId(), now);
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                LlmJobResultRefType.REVIEW_VERSION,
                reviewVersion.getId(),
                now
        );

        return new CompleteFirstReviewResult(reviewVersion, questionResults);
    }

    private void validateFirstReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private CompleteFirstReviewResult findCompletedResult(LlmJob job) {
        if (job.getResultRefType() != LlmJobResultRefType.REVIEW_VERSION
                || job.getResultRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        ReviewVersion reviewVersion = reviewVersionRepository.findById(job.getResultRefId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());
        return new CompleteFirstReviewResult(reviewVersion, questionResults);
    }

    private List<NormalizedReviewResult> validateAndNormalize(
            List<CoverLetterQuestion> questions,
            List<ReviewQuestionResultInput> inputs
    ) {
        if (questions.isEmpty() || inputs == null || inputs.size() != questions.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Map<String, ReviewQuestionResultInput> inputByQuestionId = new HashMap<>();
        for (ReviewQuestionResultInput input : inputs) {
            if (input == null || input.questionId() == null || input.questionId().isBlank()
                    || inputByQuestionId.putIfAbsent(input.questionId(), input) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        List<NormalizedReviewResult> normalizedResults = new ArrayList<>();
        for (CoverLetterQuestion question : questions) {
            ReviewQuestionResultInput input = inputByQuestionId.remove(question.getId());
            if (input == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }

            String aiReport = normalizeRequired(input.aiReport());
            String rewrittenAnswer = normalizeRequired(input.rewrittenAnswer());
            if (aiReport == null
                    || rewrittenAnswer == null
                    || countCodePoints(rewrittenAnswer) > question.getMaxAnswerLength()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            normalizedResults.add(new NormalizedReviewResult(question, aiReport, rewrittenAnswer));
        }

        if (!inputByQuestionId.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedResults;
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private record NormalizedReviewResult(
            CoverLetterQuestion question,
            String aiReport,
            String rewrittenAnswer
    ) {
    }
}
