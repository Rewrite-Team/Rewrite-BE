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

/**
 * 자기소개서별 면접 세션의 시작·재사용, 질문 목록 조회와 추가 질문 생성 요청을 처리한다.
 * 질문과 대화방의 실제 생성은 커밋 후 worker에 위임하고, 기존 세션·질문·대화는 재첨삭 후에도 유지한다.
 * 새 질문 작업은 CoverLetter 잠금 안에서 기준 버전과 진행 Job을 확인해 같은 자기소개서의 작업과 직렬화한다.
 */
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

    /**
     * 자기소개서 요약과 기존 세션을 반환하며, 세션이 없으면 결과의 세션 필드를 null로 둔다.
     * 조회에는 현재 자기소개서의 첨삭 완료 상태를 요구하지 않는다.
     * 초기·추가 질문 생성의 진행 또는 최근 실패 Job을 함께 제공해 SSE 복구에 사용하고, 답변 피드백 Job은 thread에서 조회한다.
     */
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

    /**
     * 소유자의 활성 자기소개서에 속한 세션에서 cursor보다 이전 질문을 order 내림차순으로 조회한다.
     * size보다 하나 더 읽어 다음 묶음의 존재를 판단하고, 마지막 반환 질문의 order를 다음 cursor로 만든다.
     * 질문별 thread를 일괄 조회해 연결하며, 함께 생성됐어야 할 thread가 없으면 내부 불변식 위반으로 처리한다.
     * 질문 생성 전의 빈 목록도 정상 결과이며, 생성 상태는 현재 세션·Job 조회로 확인한다.
     */
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

    /**
     * 최신 성공 첨삭 버전이 있는 자기소개서에서 초기 질문 5개를 생성할 작업을 시작한다.
     * ACTIVE 세션이면 기존 세션과 null Job을, 같은 초기 생성이 진행 중이면 기존 세션과 Job을 반환한다.
     * 세션이 없으면 생성하고 FAILED이면 같은 세션을 재사용해 QUESTION_GENERATING으로 전환한다.
     * 새 세션 상태와 Job은 함께 저장하며, 커밋 후 이벤트 리스너가 worker를 실행한다.
     */
    @Transactional
    public StartInterviewResult startMyInterview(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        validateReviewedCoverLetter(coverLetter);
        // 기준 버전 ID는 요청 시 선택하며, 해당 버전의 finalAnswer 본문은 worker 시작 시 읽는다.
        String selectedSourceReviewVersionId = coverLetter.getLatestReviewedVersionId();

        InterviewSession existingSession = interviewSessionRepository
                .findByCoverLetterId(coverLetter.getId())
                .orElse(null);
        // 이미 준비된 면접으로 진입할 때는 새 작업을 시작하지 않으므로 진행 Job 충돌 검사 전에 반환한다.
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

    /**
     * ACTIVE 세션에 요청 시점의 최신 성공 버전을 기준으로 질문 1개를 추가할 Job을 만든다.
     * 대상 자기소개서를 먼저 잠그고 같은 추가 생성 Job은 재사용하며, 다른 진행 작업은 충돌로 처리한다.
     * 기존 세션은 ACTIVE를 유지한다. 질문·thread 저장과 추가 실패 처리는 별도 worker의 Job 실행으로 이어진다.
     */
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
        // 세션의 초기 기준을 바꾸지 않고 이번 추가 Job의 입력 참조에 새 기준 버전을 기록한다.
        String sourceReviewVersionId = findLatestReviewedVersionId(coverLetter);
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

    /** 초기·추가 질문 중 최신 Job이 대기·진행·실패 상태이면 복구용으로 반환한다. */
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

    /**
     * 마지막으로 반환한 질문 order를 담은 Base64URL cursor를 복원한다.
     * cursor 생략은 첫 묶음 조회이며, 해석할 수 없거나 양수가 아닌 값은 validation 오류로 처리한다.
     */
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

    private String findLatestReviewedVersionId(CoverLetter coverLetter) {
        String latestReviewedVersionId = coverLetter.getLatestReviewedVersionId();
        reviewVersionRepository
                .findByIdAndCoverLetterId(latestReviewedVersionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return latestReviewedVersionId;
    }

    /** 초기 생성 실패는 세션 ID를 유지한 채 재시도하며, 재시도 가능 상태는 엔티티가 다시 확인한다. */
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

}
