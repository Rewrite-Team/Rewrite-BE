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
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.LlmJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 질문별 대화 메시지를 조회하고 사용자 답변과 피드백 생성 Job을 함께 저장한다.
 * 답변은 요청 트랜잭션에서 보존하며, assistant 메시지는 worker가 전체 피드백을 검증하고 완료할 때 저장한다.
 * 피드백 생성 실패에도 USER 메시지는 유지하므로 조회 결과와 Job 상태로 화면을 복구한다.
 */
@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private static final String INTERVIEW_MESSAGE_ID_PREFIX = "im";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final InterviewThreadRepository interviewThreadRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final LlmJobRepository llmJobRepository;
    private final LlmJobService llmJobService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 부모 자기소개서의 소유권·삭제 여부를 확인하고 thread의 메시지를 생성 시각·ID 오름차순으로 반환한다.
     * 최신 USER 메시지에 연결된 최신 피드백 Job을 찾아 대기·진행·실패 상태를 함께 복구한다.
     * 최초 질문은 InterviewQuestion에 있으므로 사용자가 아직 답하지 않은 thread의 빈 메시지 목록도 정상이다.
     */
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

    /**
     * 답변을 정규화·검증하고 CoverLetter를 잠근 뒤 같은 자기소개서의 진행 Job이 없는지 확인한다.
     * USER 메시지와 그 ID를 입력 참조로 가진 새 피드백 Job을 같은 트랜잭션에서 저장해 둘 중 하나만 남는 것을 막는다.
     * 충돌이면 메시지를 저장하지 않으며, 성공하면 커밋 후 worker 실행을 예약하고 메시지·Job 식별 정보를 반환한다.
     */
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
        if (llmJobService.findRunningCoverLetterJob(coverLetter.getId()) != null) {
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
        // 피드백 worker는 이 USER 메시지까지의 대화 이력을 읽고, 성공 결과를 같은 thread의 assistant 한 개로 확정한다.
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new SendInterviewMessageResult(userMessage, job);
    }

    /** 표시용 content·score를 전달하며 내부 구조화 피드백과 별도 꼬리질문 필드는 조회 결과에서 제외한다. */
    private InterviewMessageItemResult toItemResult(InterviewMessage message) {
        return new InterviewMessageItemResult(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getScore(),
                message.getCreatedAt()
        );
    }

    /** 최신 USER 답변의 Job만 찾는다. 이전 답변의 실패 이력을 현재 처리할 Job으로 되돌리지 않는다. */
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

    /** 앞뒤 공백을 제거한 답변에 필수값과 Unicode code point 기준 길이 제한을 적용한다. */
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
