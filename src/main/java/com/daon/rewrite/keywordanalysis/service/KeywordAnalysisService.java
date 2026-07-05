package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisKeywordRepository;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordAnalysisService {

    private static final String KEYWORD_ANALYSIS_ID_PREFIX = "ka";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final List<LlmJobStatus> RUNNING_JOB_STATUSES = List.of(
            LlmJobStatus.PENDING,
            LlmJobStatus.PROCESSING
    );

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final KeywordAnalysisRepository keywordAnalysisRepository;
    private final KeywordAnalysisKeywordRepository keywordAnalysisKeywordRepository;
    private final LlmJobRepository llmJobRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public StartKeywordAnalysisResult startMyKeywordAnalysis(
            String coverLetterId,
            String sourceReviewVersionId
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED
                || coverLetter.getLatestReviewVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (findRunningJob(coverLetter.getId()) != null) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        String selectedSourceReviewVersionId = selectSourceReviewVersionId(coverLetter, sourceReviewVersionId);
        Instant now = Instant.now(clock);
        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findByCoverLetterId(coverLetter.getId())
                .map(existing -> {
                    existing.restart(selectedSourceReviewVersionId);
                    return existing;
                })
                .orElseGet(() -> keywordAnalysisRepository.save(KeywordAnalysis.processing(
                        idGenerator.generate(KEYWORD_ANALYSIS_ID_PREFIX),
                        coverLetter,
                        selectedSourceReviewVersionId,
                        now
                )));

        LlmJob job = llmJobRepository.save(LlmJob.pendingKeywordAnalysis(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                now
        ));
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new StartKeywordAnalysisResult(keywordAnalysis, job);
    }

    @Transactional(readOnly = true)
    public LatestKeywordAnalysisResult findMyLatestKeywordAnalysis(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return keywordAnalysisRepository.findByCoverLetterId(coverLetter.getId())
                .map(keywordAnalysis -> {
                    List<KeywordAnalysisKeyword> keywords = keywordAnalysisKeywordRepository
                            .findByKeywordAnalysisIdOrderByKeywordOrderAsc(keywordAnalysis.getId());
                    return LatestKeywordAnalysisResult.of(coverLetter.getId(), keywordAnalysis, keywords);
                })
                .orElseGet(() -> LatestKeywordAnalysisResult.empty(coverLetter.getId()));
    }

    private String selectSourceReviewVersionId(CoverLetter coverLetter, String sourceReviewVersionId) {
        String normalizedSourceReviewVersionId = normalize(sourceReviewVersionId);
        String selectedSourceReviewVersionId = normalizedSourceReviewVersionId == null
                ? coverLetter.getLatestReviewVersionId()
                : normalizedSourceReviewVersionId;
        reviewVersionRepository
                .findByIdAndCoverLetterId(selectedSourceReviewVersionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return selectedSourceReviewVersionId;
    }

    private LlmJob findRunningJob(String coverLetterId) {
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        RUNNING_JOB_STATUSES
                )
                .orElse(null);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
