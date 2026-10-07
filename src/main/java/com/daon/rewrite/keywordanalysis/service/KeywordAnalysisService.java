package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisKeywordRepository;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.LlmJobService;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 최신 성공 첨삭 버전을 기준으로 키워드 분석을 시작하고 현재 분석 상태·결과를 조회한다.
 * 자기소개서별 분석 리소스는 하나를 재사용하며, 각 실행은 새 LlmJob으로 추적한다.
 * 외부 LLM 호출과 완료 결과 교체는 커밋 후 실행되는 키워드 worker·트랜잭션 서비스가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class KeywordAnalysisService {

    private static final String KEYWORD_ANALYSIS_ID_PREFIX = "ka";
    private static final String LLM_JOB_ID_PREFIX = "job";

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final KeywordAnalysisRepository keywordAnalysisRepository;
    private final KeywordAnalysisKeywordRepository keywordAnalysisKeywordRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final LlmJobRepository llmJobRepository;
    private final LlmJobService llmJobService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 소유자의 활성 자기소개서를 먼저 잠그고 최신 성공 버전과 진행 중 Job을 확인한다.
     * 같은 분석 Job이 진행 중이면 그대로 반환하고, 다른 종류의 Job은 충돌로 처리한다.
     * 새 실행은 분석 리소스를 생성하거나 재사용해 PROCESSING으로 바꾸고 새 Job을 같은 트랜잭션에서 저장한다.
     * 시작 응답은 AI 완료를 기다리지 않으며, 프론트엔드는 반환된 Job으로 공통 SSE를 구독한다.
     */
    @Transactional
    public StartKeywordAnalysisResult startMyKeywordAnalysis(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (runningJob != null && runningJob.getType() == LlmJobType.KEYWORD_ANALYSIS) {
            KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findByCoverLetterId(coverLetter.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
            return new StartKeywordAnalysisResult(keywordAnalysis, runningJob);
        }
        if (runningJob != null) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        // 기준 버전 ID는 여기서 선택하고, 해당 버전의 finalAnswer 본문은 worker 시작 시 읽는다.
        String selectedSourceReviewVersionId = coverLetter.getLatestReviewedVersionId();
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
        // 생성 트랜잭션이 커밋된 뒤 KeywordAnalysisJobEventListener가 worker를 실행한다.
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new StartKeywordAnalysisResult(keywordAnalysis, job);
    }

    /**
     * 현재 사용자의 활성 자기소개서와 저장된 분석 기준 버전·상태를 함께 반환한다.
     * 분석 이력이 없으면 미시작 결과를 만들고, 키워드는 COMPLETED일 때만 반환한다.
     * 진행 중이거나 최근 실패한 Job을 함께 제공해 새로고침 후 SSE 구독과 polling 복구에 사용한다.
     */
    @Transactional(readOnly = true)
    public LatestKeywordAnalysisResult findMyLatestKeywordAnalysis(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return keywordAnalysisRepository.findByCoverLetterId(coverLetter.getId())
                .map(keywordAnalysis -> {
                    var sourceReviewVersion = reviewVersionRepository
                            .findByIdAndCoverLetterId(
                                    keywordAnalysis.getSourceReviewVersionId(),
                                    coverLetter.getId()
                            )
                            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
                    LlmJob job = findLatestKeywordAnalysisJob(coverLetter.getId());
                    List<KeywordAnalysisKeyword> keywords = findCompletedKeywords(keywordAnalysis);
                    return LatestKeywordAnalysisResult.of(
                            coverLetter,
                            sourceReviewVersion,
                            keywordAnalysis,
                            job,
                            keywords
                    );
                })
                .orElseGet(() -> LatestKeywordAnalysisResult.empty(coverLetter));
    }

    /** 재분석 중·실패 시 남아 있는 이전 키워드를 현재 결과와 섞어 표시하지 않도록 빈 목록을 반환한다. */
    private List<KeywordAnalysisKeyword> findCompletedKeywords(KeywordAnalysis keywordAnalysis) {
        if (keywordAnalysis.getStatus() != KeywordAnalysisStatus.COMPLETED) {
            return List.of();
        }
        return keywordAnalysisKeywordRepository
                .findByKeywordAnalysisIdOrderByKeywordOrderAsc(keywordAnalysis.getId());
    }

    /** 최신 분석 Job 중 대기·진행·실패 상태만 복구용으로 전달하고, 완료·취소 Job은 결과에서 제외한다. */
    private LlmJob findLatestKeywordAnalysisJob(String coverLetterId) {
        LlmJob job = llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndTypeOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        LlmJobType.KEYWORD_ANALYSIS
                )
                .orElse(null);
        if (job == null || job.getStatus() == LlmJobStatus.COMPLETED
                || job.getStatus() == LlmJobStatus.CANCELED) {
            return null;
        }
        return job;
    }

}
