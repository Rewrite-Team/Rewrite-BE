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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public CoverLetterDetailResult findCurrent(String coverLetterId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        CoverLetterDetailResult.ReviewVersionResult reviewVersion = findLatestReviewVersion(coverLetter);
        LlmJob reviewJob = findCurrentReviewJob(coverLetter);

        // 진행·실패 Job의 임시 문항 결과를 우선 반환한다.
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

        // Job 결과가 없으면 최신 성공 버전을 반환한다.
        if (reviewVersion != null && coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            return new CoverLetterDetailResult(
                    coverLetter,
                    reviewVersion,
                    reviewJob,
                    findVersionQuestions(reviewVersion.value())
            );
        }

        // 첨삭 전에는 원본 문항을 반환한다.
        return new CoverLetterDetailResult(
                coverLetter,
                reviewVersion,
                reviewJob,
                findOriginalQuestions(coverLetter)
        );
    }

    @Transactional(readOnly = true)
    public CoverLetterDetailResult findVersion(String coverLetterId, String versionId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        ReviewVersion reviewVersion = reviewVersionRepository
                .findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new CoverLetterDetailResult(
                coverLetter,
                new CoverLetterDetailResult.ReviewVersionResult(
                        reviewVersion,
                        reviewVersion.getId().equals(coverLetter.getLatestReviewedVersionId())
                ),
                null,
                findVersionQuestions(reviewVersion)
        );
    }

    private CoverLetter findMyActiveCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        return coverLetterRepository.findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private CoverLetterDetailResult.ReviewVersionResult findLatestReviewVersion(CoverLetter coverLetter) {
        if (coverLetter.getLatestReviewedVersionId() == null) {
            return null;
        }
        ReviewVersion reviewVersion = reviewVersionRepository
                .findByIdAndCoverLetterId(coverLetter.getLatestReviewedVersionId(), coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return new CoverLetterDetailResult.ReviewVersionResult(reviewVersion, true);
    }

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
