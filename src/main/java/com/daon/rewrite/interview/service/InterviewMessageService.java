package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewMessageRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private static final String INTERVIEW_MESSAGE_ID_PREFIX = "im";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final List<LlmJobStatus> RUNNING_JOB_STATUSES = List.of(
            LlmJobStatus.PENDING,
            LlmJobStatus.PROCESSING
    );

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final InterviewThreadRepository interviewThreadRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final LlmJobRepository llmJobRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public InterviewMessageListResult findMyInterviewMessages(String threadId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        InterviewThread thread = interviewThreadRepository
                .findActiveByIdAndOwnerId(
                        threadId,
                        currentUser.id()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<InterviewMessageItemResult> items = interviewMessageRepository
                .findByThreadIdOrderByCreatedAtAscIdAsc(thread.getId())
                .stream()
                .map(this::toItemResult)
                .toList();
        String latestUserMessageId = interviewMessageRepository
                .findFirstByThreadIdAndRoleOrderByCreatedAtDescIdDesc(
                        thread.getId(),
                        InterviewMessageRole.USER
                )
                .map(InterviewMessage::getId)
                .orElse(null);
        LlmJob job = findLatestFeedbackJob(latestUserMessageId);

        return new InterviewMessageListResult(job == null ? null : job.getId(), items);
    }

    @Transactional
    public SendInterviewMessageResult sendMyInterviewMessage(String threadId, String content) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        InterviewThread thread = interviewThreadRepository
                .findActiveByIdAndOwnerId(threadId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String normalizedContent = validateAndNormalizeContent(content);

        String coverLetterId = thread.getInterviewSession().getCoverLetter().getId();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (hasRunningJob(coverLetter.getId())) {
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        Instant now = Instant.now(clock);
        InterviewMessage userMessage = interviewMessageRepository.save(InterviewMessage.userAnswer(
                idGenerator.generate(INTERVIEW_MESSAGE_ID_PREFIX),
                thread,
                normalizedContent,
                now
        ));
        LlmJob job = llmJobRepository.save(LlmJob.pendingInterviewMessageFeedback(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                userMessage.getId(),
                now
        ));
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new SendInterviewMessageResult(userMessage, job);
    }

    private InterviewMessageItemResult toItemResult(InterviewMessage message) {
        return new InterviewMessageItemResult(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getFeedbackSummary(),
                message.getFeedbackStrengths(),
                message.getFeedbackImprovements(),
                message.getScore(),
                message.getFollowUpQuestion(),
                message.getCreatedAt()
        );
    }

    private boolean hasRunningJob(String coverLetterId) {
        return llmJobRepository
                .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDescIdDesc(
                        LlmJobTargetType.COVER_LETTER,
                        coverLetterId,
                        RUNNING_JOB_STATUSES
                )
                .isPresent();
    }

    private LlmJob findLatestFeedbackJob(String userMessageId) {
        if (userMessageId == null) {
            return null;
        }
        LlmJob job = llmJobRepository
                .findFirstByTypeAndRequestRefTypeAndRequestRefIdOrderByCreatedAtDescIdDesc(
                        LlmJobType.INTERVIEW_MESSAGE_FEEDBACK,
                        LlmJobRequestRefType.INTERVIEW_MESSAGE,
                        userMessageId
                )
                .orElse(null);
        if (job == null || job.getStatus() == LlmJobStatus.COMPLETED
                || job.getStatus() == LlmJobStatus.CANCELED) {
            return null;
        }
        return job;
    }

    private String validateAndNormalizeContent(String content) {
        String normalized = content == null ? null : content.strip();
        if (normalized == null || normalized.isEmpty()) {
            throw validationError("면접 답변은 필수입니다.");
        }
        if (countCodePoints(normalized) > MAX_CONTENT_LENGTH) {
            throw validationError("면접 답변은 최대 2000자까지 입력할 수 있습니다.");
        }
        return normalized;
    }

    private BusinessException validationError(String reason) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new ErrorResponse.ErrorDetail("content", reason))
        );
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
