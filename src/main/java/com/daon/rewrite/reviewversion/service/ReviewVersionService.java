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
        List<NormalizedReviewResult> normalizedResults = validateAndNormalize(
                questions.stream()
                        .map(question -> new ReviewSourceQuestion(question, question.getOriginalAnswer()))
                        .toList(),
                inputs
        );

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
                    normalizedResult.source().question(),
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

    @Transactional
    public CompleteFirstReviewResult completeReReview(
            String jobId,
            List<ReviewQuestionResultInput> inputs
    ) {
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

        ReviewVersion latestVersion = reviewVersionRepository.findByIdAndCoverLetterId(
                        coverLetter.getLatestReviewVersionId(),
                        coverLetter.getId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> latestResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(latestVersion.getId());
        List<NormalizedReviewResult> normalizedResults = validateAndNormalize(
                latestResults.stream()
                        // result.getQuestion() == 원본 질문 객체 / result.getFinalAnswer == 가장 최근에 저장된 finalAnswer
                        .map(result -> new ReviewSourceQuestion(result.getQuestion(), result.getFinalAnswer()))
                        .toList(),
                inputs // 가장 최근에 ai 로 생성한 ReviewQuestionResultInput(questionId, aiReport, rewrittenAnswer) 리스트
        );

        Instant now = Instant.now(clock);
        // 성공한 재첨삭만 새 ReviewVersion으로 확정한다. 실패한 Job은 기존 최신 버전을 유지한다.
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.reReview(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                nextVersion(coverLetter.getId()),
                job.getRequestInstruction(),
                now
        ));

        List<ReviewVersionQuestionResult> questionResults = new ArrayList<>();
        for (NormalizedReviewResult normalizedResult : normalizedResults) {
            // 새 버전의 originalAnswer에는 재첨삭 기준이 된 이전 최종 작성본을 스냅샷으로 남긴다.
            questionResults.add(ReviewVersionQuestionResult.createFromSnapshot(
                    idGenerator.generate(QUESTION_RESULT_ID_PREFIX),
                    reviewVersion,
                    normalizedResult.source().question(),           // CoverLetterQuestion 객체
                    normalizedResult.source().originalAnswer(),     // 가장 최근에 저장된 finalAnswer (재첨삭 기준이 된 이전 최종 작성본)
                    normalizedResult.aiReport(),                    // 가장 최근에 ai 로 생성한 aiReport
                    normalizedResult.rewrittenAnswer()              // 가장 최근에 ai 로 생성한 rewrittenAnswer
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

    private void validateReReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_RE_REVIEW
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
            List<ReviewSourceQuestion> sources,
            List<ReviewQuestionResultInput> inputs
    ) {
        if (sources.isEmpty() || inputs == null || inputs.size() != sources.size()) {
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
        for (ReviewSourceQuestion source : sources) {
            ReviewQuestionResultInput input = inputByQuestionId.remove(source.question().getId());
            if (input == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }

            String aiReport = normalizeRequired(input.aiReport());
            String rewrittenAnswer = normalizeRequired(input.rewrittenAnswer());
            if (aiReport == null
                    || rewrittenAnswer == null
                    || countCodePoints(rewrittenAnswer) > source.question().getMaxAnswerLength()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            normalizedResults.add(new NormalizedReviewResult(source, aiReport, rewrittenAnswer));
        }

        if (!inputByQuestionId.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedResults;
    }

    private String nextVersion(String coverLetterId) {
        long nextPatch = reviewVersionRepository.countByCoverLetterId(coverLetterId) + 1;
        return "v0." + nextPatch;
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

    private record ReviewSourceQuestion(
            CoverLetterQuestion question,
            String originalAnswer
    ) {
    }

    private record NormalizedReviewResult(
            ReviewSourceQuestion source,
            String aiReport,
            String rewrittenAnswer
    ) {
    }
}
