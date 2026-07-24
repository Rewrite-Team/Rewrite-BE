package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final String INTERVIEW_SESSION_ID_PREFIX = "is";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final int MAX_INTERVIEW_QUESTION_LIST_SIZE = 20;
    private static final List<LlmJobType> INTERVIEW_QUESTION_JOB_TYPES = List.of(
            LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION,
            LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION
    );

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewThreadRepository interviewThreadRepository;
    private final LlmJobRepository llmJobRepository;
    private final LlmJobService llmJobService;
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

        LlmJob job = interviewSession == null
                ? null
                : findLatestInterviewQuestionJob(coverLetter.getId());
        return new CurrentInterviewResult(coverLetter, interviewSession, job);
    }

    @Transactional(readOnly = true)
    public InterviewQuestionListResult findMyInterviewQuestions(
            String interviewSessionId,
            String cursor,
            int size
    ) {
        validateInterviewQuestionListSize(size);
        int cursorOrder = decodeInterviewQuestionCursor(cursor);
        CurrentUser currentUser = currentUserProvider.currentUser();
        InterviewSession interviewSession = interviewSessionRepository
                .findByIdAndCoverLetterOwnerIdAndCoverLetterDeletedAtIsNull(
                        interviewSessionId,
                        currentUser.id()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        List<InterviewQuestion> foundQuestions = interviewQuestionRepository
                .findByInterviewSessionIdAndQuestionOrderLessThanOrderByQuestionOrderDesc(
                        interviewSession.getId(),
                        cursorOrder,
                        PageRequest.of(0, size + 1)
                );
        boolean hasNext = foundQuestions.size() > size;
        List<InterviewQuestion> questions = foundQuestions.stream()
                .limit(size)
                .toList();
        List<String> questionIds = questions.stream()
                .map(InterviewQuestion::getId)
                .toList();
        Map<String, String> threadIdsByQuestionId = (questionIds.isEmpty()
                ? List.<InterviewThread>of()
                : interviewThreadRepository.findByInterviewQuestionIdIn(questionIds))
                .stream()
                .collect(Collectors.toMap(
                        thread -> thread.getInterviewQuestion().getId(),
                        thread -> thread.getId()
                ));
        List<InterviewQuestionItemResult> items = questions
                .stream()
                .map(question -> toQuestionItem(question, threadIdsByQuestionId))
                .toList();
        String nextCursor = hasNext
                ? encodeInterviewQuestionCursor(questions.getLast().getQuestionOrder())
                : null;

        return new InterviewQuestionListResult(items, nextCursor);
    }

    @Transactional
    public StartInterviewResult startMyInterview(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        validateReviewedCoverLetter(coverLetter);
        String selectedSourceReviewVersionId = coverLetter.getLatestReviewedVersionId();

        InterviewSession existingSession = interviewSessionRepository
                .findByCoverLetterId(coverLetter.getId())
                .orElse(null);
        if (existingSession != null && existingSession.getStatus() == InterviewSessionStatus.ACTIVE) {
            return new StartInterviewResult(existingSession, null);
        }
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (existingSession != null
                && existingSession.getStatus() == InterviewSessionStatus.QUESTION_GENERATING
                && runningJob != null
                && runningJob.getType() == LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION) {
            return new StartInterviewResult(existingSession, runningJob);
        }
        if (runningJob != null) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        Instant now = Instant.now(clock);
        InterviewSession interviewSession = startQuestionGeneration(
                existingSession,
                coverLetter,
                selectedSourceReviewVersionId,
                now
        );
        LlmJob job = llmJobRepository.save(LlmJob.pendingInitialInterviewQuestionGeneration(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                selectedSourceReviewVersionId,
                now
        ));
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new StartInterviewResult(interviewSession, job);
    }

    @Transactional
    public AddInterviewQuestionResult addMyInterviewQuestion(String interviewSessionId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        String coverLetterId = interviewSessionRepository
                .findActiveCoverLetterIdByIdAndOwnerId(interviewSessionId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        InterviewSession interviewSession = interviewSessionRepository.findById(interviewSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        if (interviewSession.getStatus() != InterviewSessionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        validateReviewedCoverLetter(coverLetter);
        String sourceReviewVersionId = selectSourceReviewVersionId(coverLetter, null);
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (runningJob != null && runningJob.getType() == LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION) {
            return new AddInterviewQuestionResult(interviewSession, runningJob);
        }
        if (runningJob != null) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        Instant now = Instant.now(clock);
        LlmJob job = llmJobRepository.save(LlmJob.pendingAdditionalInterviewQuestionGeneration(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                sourceReviewVersionId,
                now
        ));
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new AddInterviewQuestionResult(interviewSession, job);
    }

    private void validateReviewedCoverLetter(CoverLetter coverLetter) {
        if (coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private LlmJob findLatestInterviewQuestionJob(String coverLetterId) {
        LlmJob job = llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndTypeInOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        INTERVIEW_QUESTION_JOB_TYPES
                )
                .orElse(null);
        if (job == null || job.getStatus() == LlmJobStatus.COMPLETED
                || job.getStatus() == LlmJobStatus.CANCELED) {
            return null;
        }
        return job;
    }

    private InterviewQuestionItemResult toQuestionItem(
            InterviewQuestion question,
            Map<String, String> threadIdsByQuestionId
    ) {
        return new InterviewQuestionItemResult(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestion(),
                findRequiredThreadId(question.getId(), threadIdsByQuestionId)
        );
    }

    private void validateInterviewQuestionListSize(int size) {
        if (size < 1 || size > MAX_INTERVIEW_QUESTION_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private int decodeInterviewQuestionCursor(String cursor) {
        if (cursor == null) {
            return Integer.MAX_VALUE;
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            int questionOrder = Integer.parseInt(decoded);
            if (questionOrder < 1) {
                throw new IllegalArgumentException("질문 순서는 1 이상이어야 합니다.");
            }
            return questionOrder;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String encodeInterviewQuestionCursor(int questionOrder) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Integer.toString(questionOrder).getBytes(StandardCharsets.UTF_8));
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
                ? coverLetter.getLatestReviewedVersionId()
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
