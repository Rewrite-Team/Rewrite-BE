package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final String INTERVIEW_SESSION_ID_PREFIX = "is";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final List<LlmJobStatus> RUNNING_JOB_STATUSES = List.of(
            LlmJobStatus.PENDING,
            LlmJobStatus.PROCESSING
    );

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewThreadRepository interviewThreadRepository;
    private final LlmJobRepository llmJobRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public CurrentInterviewResult findMyCurrentInterview(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        InterviewSession interviewSession = interviewSessionRepository
                .findByCoverLetterId(coverLetter.getId())
                .orElse(null);

        return new CurrentInterviewResult(coverLetter.getId(), interviewSession);
    }

    @Transactional(readOnly = true)
    public InterviewQuestionListResult findMyInterviewQuestions(String interviewSessionId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        InterviewSession interviewSession = interviewSessionRepository
                .findByIdAndCoverLetterOwnerIdAndCoverLetterDeletedAtIsNull(
                        interviewSessionId,
                        currentUser.id()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Map<String, String> threadIdsByQuestionId = interviewThreadRepository
                .findByInterviewSessionId(interviewSession.getId())
                .stream()
                .collect(Collectors.toMap(
                        thread -> thread.getInterviewQuestion().getId(),
                        thread -> thread.getId()
                ));
        List<InterviewQuestionItemResult> items = interviewQuestionRepository
                .findByInterviewSessionIdOrderByQuestionOrderAsc(interviewSession.getId())
                .stream()
                .map(question -> toQuestionItem(question, threadIdsByQuestionId))
                .toList();

        return new InterviewQuestionListResult(interviewSession.getId(), items);
    }

    @Transactional
    public StartInterviewResult startMyInterview(String coverLetterId, String sourceReviewVersionId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        validateReviewedCoverLetter(coverLetter);
        String selectedSourceReviewVersionId = selectSourceReviewVersionId(coverLetter, sourceReviewVersionId);

        InterviewSession existingSession = interviewSessionRepository
                .findByCoverLetterId(coverLetter.getId())
                .orElse(null);
        if (isReusableSession(existingSession)) {
            return new StartInterviewResult(existingSession, null);
        }
        if (hasRunningJob(coverLetter.getId())) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        Instant now = Instant.now(clock);
        InterviewSession interviewSession = startQuestionGeneration(
                existingSession,
                coverLetter,
                selectedSourceReviewVersionId,
                now
        );
        LlmJob job = llmJobRepository.save(LlmJob.pendingInterviewQuestionGeneration(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                now
        ));
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new StartInterviewResult(interviewSession, job);
    }

    private void validateReviewedCoverLetter(CoverLetter coverLetter) {
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED
                || coverLetter.getLatestReviewVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private boolean isReusableSession(InterviewSession interviewSession) {
        if (interviewSession == null) {
            return false;
        }
        return interviewSession.getStatus() == InterviewSessionStatus.ACTIVE
                || interviewSession.getStatus() == InterviewSessionStatus.QUESTION_GENERATING;
    }

    private boolean hasRunningJob(String coverLetterId) {
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        RUNNING_JOB_STATUSES
                )
                .isPresent();
    }

    private InterviewQuestionItemResult toQuestionItem(
            InterviewQuestion question,
            Map<String, String> threadIdsByQuestionId
    ) {
        return new InterviewQuestionItemResult(
                question.getId(),
                question.getSourceReviewVersionId(),
                question.getQuestionOrder(),
                question.getQuestion(),
                findRequiredThreadId(question.getId(), threadIdsByQuestionId)
        );
    }

    private String findRequiredThreadId(String questionId, Map<String, String> threadIdsByQuestionId) {
        String threadId = threadIdsByQuestionId.get(questionId);
        if (threadId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return threadId;
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

    private InterviewSession startQuestionGeneration(
            InterviewSession existingSession,
            CoverLetter coverLetter,
            String sourceReviewVersionId,
            Instant now
    ) {
        if (existingSession != null) {
            existingSession.restartQuestionGeneration(sourceReviewVersionId);
            return existingSession;
        }
        return interviewSessionRepository.save(InterviewSession.questionGenerating(
                idGenerator.generate(INTERVIEW_SESSION_ID_PREFIX),
                coverLetter,
                sourceReviewVersionId,
                now
        ));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
