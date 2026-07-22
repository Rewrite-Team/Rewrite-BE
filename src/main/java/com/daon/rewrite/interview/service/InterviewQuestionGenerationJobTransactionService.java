package com.daon.rewrite.interview.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationAnswer;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationClientException;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationRequest;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationResult;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.CoverLetterJobLockService;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class InterviewQuestionGenerationJobTransactionService {

    private static final int INITIAL_QUESTION_COUNT = 5;
    private static final int ADDITIONAL_QUESTION_COUNT = 1;
    private static final String INTERVIEW_QUESTION_ID_PREFIX = "iq";
    private static final String INTERVIEW_THREAD_ID_PREFIX = "it";
    private static final String STARTED_MESSAGE = "면접 질문 생성을 시작합니다.";
    private static final String COMPLETED_MESSAGE = "면접 질문 생성이 완료되었습니다.";
    private static final String FAILED_MESSAGE = "면접 질문 생성에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "면접 질문 생성 처리 중 오류가 발생했습니다.";

    private final LlmJobRepository llmJobRepository;
    private final CoverLetterJobLockService coverLetterJobLockService;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewThreadRepository interviewThreadRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public InterviewQuestionGenerationWork start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateInterviewQuestionGenerationJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        if (coverLetter.getLatestReviewedVersionId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        InterviewSession interviewSession = interviewSessionRepository.findByCoverLetterId(coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<InterviewQuestion> existingQuestions = interviewQuestionRepository
                .findByInterviewSessionIdOrderByQuestionOrderAsc(interviewSession.getId());
        GenerationMode generationMode = resolveGenerationMode(job);
        validateStartState(job, interviewSession, existingQuestions, generationMode);

        ReviewVersion sourceReviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(
                        sourceReviewVersionId(job),
                        coverLetter.getId()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(sourceReviewVersion.getId());
        if (questionResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        job.startProcessing(STARTED_MESSAGE);
        return new InterviewQuestionGenerationWork(new InterviewQuestionGenerationRequest(
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getPreferences(),
                generationMode.questionCount,
                existingQuestions.stream()
                        .map(InterviewQuestion::getQuestion)
                        .toList(),
                questionResults.stream()
                        .map(result -> new InterviewQuestionGenerationAnswer(
                                result.getQuestion().getId(),
                                result.getQuestionOrder(),
                                result.getQuestionText(),
                                result.getFinalAnswer()
                        ))
                        .toList()
        ));
    }

    @Transactional
    public void complete(String jobId, List<InterviewQuestionGenerationResult> results) {
        LlmJob job = findInterviewQuestionGenerationJobForUpdate(jobId);
        if (job.getStatus() == LlmJobStatus.COMPLETED || job.getStatus() == LlmJobStatus.FAILED) {
            return;
        }
        if (job.getStatus() != LlmJobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        InterviewSession interviewSession = interviewSessionRepository.findByCoverLetterId(job.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        List<InterviewQuestion> existingQuestions = interviewQuestionRepository
                .findByInterviewSessionIdOrderByQuestionOrderAsc(interviewSession.getId());
        GenerationMode generationMode = resolveGenerationMode(job);
        validateCompletionState(job, interviewSession, existingQuestions, results, generationMode);
        String sourceReviewVersionId = sourceReviewVersionId(job);
        int firstQuestionOrder = generationMode == GenerationMode.INITIAL
                ? 1
                : existingQuestions.getLast().getQuestionOrder() + 1;

        List<InterviewQuestion> questions = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            InterviewQuestionGenerationResult result = results.get(index);
            questions.add(InterviewQuestion.create(
                    idGenerator.generate(INTERVIEW_QUESTION_ID_PREFIX),
                    interviewSession,
                    sourceReviewVersionId,
                    firstQuestionOrder + index,
                    InterviewQuestionType.COVER_LETTER_BASED,
                    result.question()
            ));
        }
        interviewQuestionRepository.saveAll(questions);

        Instant now = Instant.now(clock);
        interviewThreadRepository.saveAll(questions.stream()
                .map(question -> InterviewThread.active(
                        idGenerator.generate(INTERVIEW_THREAD_ID_PREFIX),
                        interviewSession,
                        question,
                        now
                ))
                .toList());
        if (generationMode == GenerationMode.INITIAL) {
            interviewSession.activate();
        }
        LlmJobResultRefType resultRefType = generationMode == GenerationMode.INITIAL
                ? LlmJobResultRefType.INTERVIEW_SESSION
                : LlmJobResultRefType.INTERVIEW_QUESTION;
        String resultRefId = generationMode == GenerationMode.INITIAL
                ? interviewSession.getId()
                : questions.getFirst().getId();
        job.markCompleted(
                job.getProgressTotal(),
                COMPLETED_MESSAGE,
                resultRefType,
                resultRefId,
                now
        );
    }

    @Transactional
    public void fail(String jobId, InterviewQuestionGenerationClientException.Reason reason) {
        LlmJob job = findInterviewQuestionGenerationJobForUpdate(jobId);
        if (job.getStatus() == LlmJobStatus.COMPLETED || job.getStatus() == LlmJobStatus.FAILED) {
            return;
        }

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                now
        );
        interviewSessionRepository.findByCoverLetterId(job.getTargetId())
                .filter(session -> session.getStatus() == InterviewSessionStatus.QUESTION_GENERATING)
                .ifPresent(InterviewSession::fail);
    }

    @Transactional
    public void failUnexpected(String jobId) {
        LlmJob job = findInterviewQuestionGenerationJobForUpdate(jobId);
        if (job.getStatus() == LlmJobStatus.COMPLETED || job.getStatus() == LlmJobStatus.FAILED) {
            return;
        }

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                UNEXPECTED_ERROR_CODE,
                UNEXPECTED_ERROR_MESSAGE,
                now
        );
        interviewSessionRepository.findByCoverLetterId(job.getTargetId())
                .filter(session -> session.getStatus() == InterviewSessionStatus.QUESTION_GENERATING)
                .ifPresent(InterviewSession::fail);
    }

    private LlmJob findInterviewQuestionGenerationJobForUpdate(String jobId) {
        LlmJob job = llmJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (!job.getType().isInterviewQuestionGeneration()
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER
                || job.getRequestRefType() != LlmJobRequestRefType.REVIEW_VERSION
                || job.getRequestRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private LlmJob validateInterviewQuestionGenerationJob(LlmJob job) {
        if (!job.getType().isInterviewQuestionGeneration()
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER
                || job.getRequestRefType() != LlmJobRequestRefType.REVIEW_VERSION
                || job.getRequestRefId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private GenerationMode resolveGenerationMode(LlmJob job) {
        if (job.getType() == LlmJobType.INTERVIEW_INITIAL_QUESTION_GENERATION) {
            return GenerationMode.INITIAL;
        }
        if (job.getType() == LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION) {
            return GenerationMode.ADDITIONAL;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private void validateStartState(
            LlmJob job,
            InterviewSession interviewSession,
            List<InterviewQuestion> existingQuestions,
            GenerationMode generationMode
    ) {
        if (job.getProgressTotal() != generationMode.questionCount) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (generationMode == GenerationMode.INITIAL) {
            if (interviewSession.getStatus() != InterviewSessionStatus.QUESTION_GENERATING
                    || !existingQuestions.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            return;
        }
        if (interviewSession.getStatus() != InterviewSessionStatus.ACTIVE || existingQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateCompletionState(
            LlmJob job,
            InterviewSession interviewSession,
            List<InterviewQuestion> existingQuestions,
            List<InterviewQuestionGenerationResult> results,
            GenerationMode generationMode
    ) {
        validateStartState(job, interviewSession, existingQuestions, generationMode);
        if (results == null || results.size() != generationMode.questionCount) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String sourceReviewVersionId(LlmJob job) {
        return job.getRequestRefId();
    }

    private String errorCode(InterviewQuestionGenerationClientException.Reason reason) {
        if (reason == InterviewQuestionGenerationClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(InterviewQuestionGenerationClientException.Reason reason) {
        if (reason == InterviewQuestionGenerationClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }

    private enum GenerationMode {
        INITIAL(INITIAL_QUESTION_COUNT),
        ADDITIONAL(ADDITIONAL_QUESTION_COUNT);

        private final int questionCount;

        GenerationMode(int questionCount) {
            this.questionCount = questionCount;
        }
    }
}
