package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 현재 자기소개서 상세와 선택한 첨삭 버전 상세를 같은 조회 결과 구조로 조합한다.
 * WRITING 원본, 진행·실패 Job의 입력 및 임시 결과, 성공 버전의 확정 결과 중 화면에 맞는 문항을 선택한다.
 * 사용자 조회는 항상 자기소개서 소유권과 soft delete 여부를 먼저 확인한다.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterDetailQueryService {

    private static final List<LlmJobType> REVIEW_JOB_TYPES = List.of(
            LlmJobType.COVER_LETTER_REVIEW,
            LlmJobType.COVER_LETTER_RE_REVIEW
    );
    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterQuestionRepository coverLetterQuestionRepository;
    private final LlmJobRepository llmJobRepository;
    private final ReviewJobQuestionResultRepository reviewJobQuestionResultRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository reviewVersionQuestionResultRepository;

    /**
     * 진행·실패 중이면 해당 첨삭 시도를, 완료 상태이면 최신 성공 버전을 현재 상세로 선택한다.
     * 문항은 현재 Job의 임시 결과 → 선택한 버전의 확정 결과 → 원본 순서로 찾는다.
     * 여러 조회 사이의 worker 커밋으로 상태와 문항이 섞이지 않도록 REPEATABLE_READ 스냅샷에서 읽는다.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CoverLetterDetailResult findCurrent(String coverLetterId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        LlmJob reviewJob = findCurrentReviewJob(coverLetter);
        ReviewVersion currentVersion = reviewJob == null ? null : reviewVersionRepository
                .findByLlmJobId(reviewJob.getId()).orElse(null);
        if (currentVersion == null) {
            currentVersion = findLatestReviewVersion(coverLetter);
        }
        CoverLetterDetailResult.ReviewVersionResult reviewVersion = toVersionResult(coverLetter, currentVersion);

        // 임시 문항에는 실제 Job 입력과 완료된 새 AI 결과가 들어 있어 진행·실패 화면을 복원할 수 있다.
        if (reviewJob != null) {
            List<ReviewJobQuestionResult> jobResults = reviewJobQuestionResultRepository
                    .findByLlmJobIdOrderByQuestionOrderAsc(reviewJob.getId());
            if (!jobResults.isEmpty()) {
                return new CoverLetterDetailResult(
                        coverLetter,
                        reviewVersion,
                        reviewJob,
                        jobResults.stream().map(this::fromJobResult).toList()
                );
            }
        }

        // Job 입력 스냅샷이 없을 때 선택한 버전에 확정 결과가 있으면 사용한다.
        if (reviewVersion != null && coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            List<CoverLetterDetailResult.QuestionResult> versionQuestions =
                    findVersionQuestions(reviewVersion.value());
            if (!versionQuestions.isEmpty()) {
                return new CoverLetterDetailResult(coverLetter, reviewVersion, reviewJob, versionQuestions);
            }
        }

        // WRITING 또는 최초 첨삭 worker가 임시 문항을 만들기 전에는 저장된 원본을 보여준다.
        return new CoverLetterDetailResult(
                coverLetter,
                reviewVersion,
                reviewJob,
                findOriginalQuestions(coverLetter)
        );
    }

    /**
     * 히스토리에서 선택한 버전을 조회하며, 자기소개서 표시 상태는 현재 값으로 유지한다.
     * 미완료·실패 버전은 해당 Job과 임시 문항을, 성공 버전은 확정 문항을 반환한다.
     * Job 연결이 없는 기존 성공 버전도 확정 결과로 읽는다.
     */
    @Transactional(readOnly = true)
    public CoverLetterDetailResult findVersion(String coverLetterId, String versionId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        ReviewVersion reviewVersion = reviewVersionRepository
                .findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LlmJob job = reviewVersion.getLlmJob();
        if (job != null && job.getStatus() != LlmJobStatus.COMPLETED) {
            // 성공 문항 외에도 진행·실패 문항의 입력을 유지하고, 아직 없는 AI 필드는 null로 전달한다.
            List<ReviewJobQuestionResult> jobResults = reviewJobQuestionResultRepository
                    .findByLlmJobIdOrderByQuestionOrderAsc(job.getId());
            return new CoverLetterDetailResult(
                    coverLetter,
                    toVersionResult(coverLetter, reviewVersion),
                    job,
                    // 최초 첨삭 worker 시작 전처럼 임시 행이 없으면 원본으로 복원한다.
                    jobResults.isEmpty() ? findOriginalQuestions(coverLetter)
                            : jobResults.stream().map(this::fromJobResult).toList()
            );
        }
        return new CoverLetterDetailResult(
                coverLetter,
                toVersionResult(coverLetter, reviewVersion),
                null,
                findVersionQuestions(reviewVersion)
        );
    }

    private CoverLetter findMyActiveCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        return coverLetterRepository.findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 최신 시도와 별개로 CoverLetter에 저장된 최신 성공 버전을 찾는다. */
    private ReviewVersion findLatestReviewVersion(CoverLetter coverLetter) {
        if (coverLetter.getLatestReviewedVersionId() == null) {
            return null;
        }
        ReviewVersion reviewVersion = reviewVersionRepository
                .findByIdAndCoverLetterId(coverLetter.getLatestReviewedVersionId(), coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return reviewVersion;
    }

    /**
     * latest는 실패·취소도 포함한 마지막 시도, latestReviewed는 최신 성공 참조와의 일치 여부다.
     * 시도마다 버전 번호를 하나씩 부여하므로 마지막 시도는 전체 버전 수에 해당하는 라벨로 식별한다.
     */
    private CoverLetterDetailResult.ReviewVersionResult toVersionResult(
            CoverLetter coverLetter,
            ReviewVersion reviewVersion
    ) {
        if (reviewVersion == null) {
            return null;
        }
        long versionCount = reviewVersionRepository.countByCoverLetterId(coverLetter.getId());
        return new CoverLetterDetailResult.ReviewVersionResult(
                reviewVersion,
                reviewVersion.getVersion().equals("v0." + versionCount),
                reviewVersion.getId().equals(coverLetter.getLatestReviewedVersionId())
        );
    }

    /** 표시 상태에 대응하는 첨삭 Job만 선택한다. 키워드·면접 Job은 자기소개서 상세의 reviewJob에 포함하지 않는다. */
    private LlmJob findCurrentReviewJob(CoverLetter coverLetter) {
        List<LlmJobStatus> statuses;
        if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING) {
            statuses = List.of(LlmJobStatus.PENDING, LlmJobStatus.PROCESSING);
        } else if (coverLetter.getStatus() == CoverLetterStatus.REVIEW_FAILED) {
            statuses = List.of(LlmJobStatus.FAILED);
        } else {
            return null;
        }
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndTypeInAndStatusInOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetter.getId(),
                        REVIEW_JOB_TYPES,
                        statuses
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    private List<CoverLetterDetailResult.QuestionResult> findOriginalQuestions(CoverLetter coverLetter) {
        return coverLetterQuestionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId())
                .stream()
                .map(this::fromOriginalQuestion)
                .toList();
    }

    private List<CoverLetterDetailResult.QuestionResult> findVersionQuestions(ReviewVersion reviewVersion) {
        return reviewVersionQuestionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId())
                .stream()
                .map(this::fromVersionResult)
                .toList();
    }

    private CoverLetterDetailResult.QuestionResult fromOriginalQuestion(CoverLetterQuestion question) {
        return new CoverLetterDetailResult.QuestionResult(
                null,
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestion(),
                question.getMaxAnswerLength(),
                question.getOriginalAnswer(),
                length(question.getOriginalAnswer()),
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Job에 고정한 입력을 originalAnswer로 전달한다. 재첨삭이면 이전 최신 성공 버전의 finalAnswer다.
     * 임시 결과는 읽기 전용이므로 확정 문항 결과 ID를 부여하지 않는다.
     */
    private CoverLetterDetailResult.QuestionResult fromJobResult(ReviewJobQuestionResult result) {
        return new CoverLetterDetailResult.QuestionResult(
                null,
                result.getQuestion().getId(),
                result.getQuestionOrder(),
                result.getQuestionText(),
                result.getMaxAnswerLength(),
                result.getInputAnswer(),
                result.getInputAnswerLength(),
                result.getAiReport(),
                result.getRewrittenAnswer(),
                result.getRewrittenAnswerLength(),
                result.getFinalAnswer(),
                result.getFinalAnswerLength()
        );
    }

    private CoverLetterDetailResult.QuestionResult fromVersionResult(ReviewVersionQuestionResult result) {
        return new CoverLetterDetailResult.QuestionResult(
                result.getId(),
                result.getQuestion().getId(),
                result.getQuestionOrder(),
                result.getQuestionText(),
                result.getMaxAnswerLength(),
                result.getOriginalAnswer(),
                result.getOriginalAnswerLength(),
                result.getAiReport(),
                result.getRewrittenAnswer(),
                result.getRewrittenAnswerLength(),
                result.getFinalAnswer(),
                result.getFinalAnswerLength()
        );
    }

    private Integer length(String value) {
        return value == null ? null : value.codePointCount(0, value.length());
    }
}
