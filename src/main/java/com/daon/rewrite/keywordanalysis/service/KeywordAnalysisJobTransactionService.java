package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisAnswer;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClientException;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisRequest;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisResult;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisKeywordRepository;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.CoverLetterJobLockService;
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
import java.util.List;

@Service
@RequiredArgsConstructor
class KeywordAnalysisJobTransactionService {

    private static final String KEYWORD_ID_PREFIX = "kak";
    private static final String STARTED_MESSAGE = "키워드 분석을 시작합니다.";
    private static final String COMPLETED_MESSAGE = "키워드 분석이 완료되었습니다.";
    private static final String FAILED_MESSAGE = "키워드 분석에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "키워드 분석 처리 중 오류가 발생했습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterJobLockService coverLetterJobLockService;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final KeywordAnalysisRepository keywordAnalysisRepository;
    private final KeywordAnalysisKeywordRepository keywordRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public KeywordAnalysisWork start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateKeywordAnalysisJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        if (coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findByCoverLetterId(coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (keywordAnalysis.getStatus() != KeywordAnalysisStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        ReviewVersion sourceReviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(
                        keywordAnalysis.getSourceReviewVersionId(),
                        coverLetter.getId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(sourceReviewVersion.getId());
        if (questionResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new KeywordAnalysisWork(new KeywordAnalysisRequest(
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getPreferences(),
                questionResults.stream()
                        .map(result -> new KeywordAnalysisAnswer(
                                result.getQuestion().getId(),
                                result.getQuestionOrder(),
                                result.getQuestionText(),
                                result.getFinalAnswer()
                        ))
                        .toList()
        ));
    }

    @Transactional
    public void complete(String jobId, List<KeywordAnalysisResult> results) {
        LlmJob job = findKeywordAnalysisJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findByCoverLetterId(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (keywordAnalysis.getStatus() != KeywordAnalysisStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        keywordRepository.deleteByKeywordAnalysisId(keywordAnalysis.getId());
        keywordRepository.flush();
        List<KeywordAnalysisKeyword> keywords = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            KeywordAnalysisResult result = results.get(index);
            keywords.add(KeywordAnalysisKeyword.of(
                    idGenerator.generate(KEYWORD_ID_PREFIX),
                    keywordAnalysis,
                    index + 1,
                    result.keyword(),
                    result.importance()
            ));
        }
        keywordRepository.saveAll(keywords);

        Instant now = Instant.now(clock);
        keywordAnalysis.complete(now);
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                LlmJobResultRefType.KEYWORD_ANALYSIS,
                keywordAnalysis.getId(),
                now
        );
    }

    @Transactional
    public void fail(String jobId, KeywordAnalysisClientException.Reason reason) {
        LlmJob job = findKeywordAnalysisJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }

        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findByCoverLetterId(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (keywordAnalysis.getStatus() != KeywordAnalysisStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                now
        );
        keywordAnalysis.fail(now);
    }

    @Transactional
    public void failUnexpected(String jobId) {
        LlmJob job = findKeywordAnalysisJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_MESSAGE,
                now
        );
        keywordAnalysisRepository.findByCoverLetterId(job.getTargetId())
                .filter(keywordAnalysis -> keywordAnalysis.getStatus() == KeywordAnalysisStatus.PROCESSING)
                .ifPresent(keywordAnalysis -> keywordAnalysis.fail(now));
    }

    private LlmJob findKeywordAnalysisJobForUpdate(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (job.getType() != LlmJobType.KEYWORD_ANALYSIS
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private LlmJob validateKeywordAnalysisJob(LlmJob job) {
        if (job.getType() != LlmJobType.KEYWORD_ANALYSIS
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private String errorCode(KeywordAnalysisClientException.Reason reason) {
        if (reason == KeywordAnalysisClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(KeywordAnalysisClientException.Reason reason) {
        if (reason == KeywordAnalysisClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }
}
