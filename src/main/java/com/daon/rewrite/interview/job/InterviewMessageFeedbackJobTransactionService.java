package com.daon.rewrite.interview.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackClientException;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackMessage;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackRequest;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackResult;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.entity.InterviewThreadStatus;
import com.daon.rewrite.interview.repository.InterviewMessageRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.CoverLetterJobLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
class InterviewMessageFeedbackJobTransactionService {

    private static final String INTERVIEW_MESSAGE_ID_PREFIX = "im";
    private static final String STARTED_MESSAGE = "면접 답변 피드백 생성을 시작합니다.";
    private static final String COMPLETED_MESSAGE = "면접 답변 피드백 생성이 완료되었습니다.";
    private static final String FAILED_MESSAGE = "면접 답변 피드백 생성에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "면접 답변 피드백 처리 중 오류가 발생했습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterJobLockService coverLetterJobLockService;
    private final InterviewMessageRepository interviewMessageRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public InterviewMessageFeedbackWork start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateInterviewMessageFeedbackJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        InterviewMessage userMessage = findRequestUserMessage(job);
        if (!userMessage.getThread().getInterviewSession().getCoverLetter().getId().equals(coverLetter.getId())
                || userMessage.getThread().getInterviewSession().getStatus() != InterviewSessionStatus.ACTIVE
                || userMessage.getThread().getStatus() != InterviewThreadStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        List<InterviewMessage> messages = interviewMessageRepository
                .findByThreadIdOrderByCreatedAtAscIdAsc(userMessage.getThread().getId());
        if (messages.isEmpty() || !messages.get(messages.size() - 1).getId().equals(userMessage.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new InterviewMessageFeedbackWork(new InterviewMessageFeedbackRequest(
                userMessage.getThread().getInterviewQuestion().getQuestion(),
                messages.stream()
                        .map(message -> new InterviewMessageFeedbackMessage(message.getRole(), message.getContent()))
                        .toList()
        ));
    }

    @Transactional
    public void complete(String jobId, InterviewMessageFeedbackResult result) {
        LlmJob job = findInterviewMessageFeedbackJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        InterviewMessage userMessage = findRequestUserMessage(job);
        Instant now = Instant.now(clock);
        InterviewMessage assistantMessage = interviewMessageRepository.save(InterviewMessage.assistantFeedback(
                idGenerator.generate(INTERVIEW_MESSAGE_ID_PREFIX),
                userMessage.getThread(),
                result.content(),
                result.feedbackSummary(),
                result.feedbackStrengths(),
                result.feedbackImprovements(),
                result.score(),
                result.followUpQuestion(),
                now
        ));
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                LlmJobResultRefType.INTERVIEW_MESSAGE,
                assistantMessage.getId(),
                now
        );
    }

    @Transactional
    public void fail(String jobId, InterviewMessageFeedbackClientException.Reason reason) {
        LlmJob job = findInterviewMessageFeedbackJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }

        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                Instant.now(clock)
        );
    }

    @Transactional
    public void failUnexpected(String jobId) {
        LlmJob job = findInterviewMessageFeedbackJobForUpdate(jobId);
        if (job.getStatus().isTerminal()) {
            return;
        }

        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_MESSAGE,
                Instant.now(clock)
        );
    }

    private LlmJob findInterviewMessageFeedbackJobForUpdate(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (job.getType() != LlmJobType.INTERVIEW_MESSAGE_FEEDBACK
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private LlmJob validateInterviewMessageFeedbackJob(LlmJob job) {
        if (job.getType() != LlmJobType.INTERVIEW_MESSAGE_FEEDBACK
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private InterviewMessage findRequestUserMessage(LlmJob job) {
        if (job.getRequestRefType() != LlmJobRequestRefType.INTERVIEW_MESSAGE
                || job.getRequestRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        InterviewMessage message = interviewMessageRepository.findById(job.getRequestRefId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (message.getRole() != InterviewMessageRole.USER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return message;
    }

    private String errorCode(InterviewMessageFeedbackClientException.Reason reason) {
        if (reason == InterviewMessageFeedbackClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(InterviewMessageFeedbackClientException.Reason reason) {
        if (reason == InterviewMessageFeedbackClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }
}
