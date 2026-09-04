package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.service.CoverLetterJobLockService;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.client.ReviewQuestion;
import com.daon.rewrite.reviewversion.client.ReviewRequest;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
class FirstReviewJobTransactionService {

    private static final String STARTED_MESSAGE = "첨삭을 시작합니다.";
    private static final String FAILED_MESSAGE = "LLM 첨삭에 실패했습니다.";
    private static final String PROVIDER_ERROR_CODE = "LLM_PROVIDER_ERROR";
    private static final String PROVIDER_ERROR_MESSAGE = "LLM 응답 생성에 실패했습니다.";
    private static final String OUTPUT_VALIDATION_ERROR_CODE = "LLM_OUTPUT_VALIDATION_FAILED";
    private static final String OUTPUT_VALIDATION_ERROR_MESSAGE = "LLM 출력 형식이 올바르지 않습니다.";
    private static final String UNEXPECTED_ERROR_CODE = "INTERNAL_ERROR";
    private static final String UNEXPECTED_ERROR_MESSAGE = "첨삭 처리 중 오류가 발생했습니다.";
    private static final String JOB_QUESTION_RESULT_ID_PREFIX = "rjqr";

    private final CoverLetterJobLockService coverLetterJobLockService;
    private final CoverLetterQuestionRepository questionRepository;
    private final ReviewJobQuestionResultRepository jobQuestionResultRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public ReviewWork start(String jobId) {
        // jobId에 연결된 LlmJob 과 CoverLetter를 조회하면서 PESSIMISTIC_WRITE를 획득
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        // Job type이 COVER_LETTER_REVIEW인지, Job targetType 이 COVER_LETTER 인지 확인
        LlmJob job = validateFirstReviewJob(locked.job());
        // PENDING Job 만 처리
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        // 정상적인 상황에서 자소서 상태는 REVIEWING 이어야 함
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 자소서 문항을 questionOrder 오름차순으로 가져옴
        List<CoverLetterQuestion> questions = questionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // AI응답을 받기 전 임시 처리 결과 객체인 ReviewJobQuestionResult 을 각 문항마다 생성
        jobQuestionResultRepository.saveAll(questions.stream()
                .map(question -> ReviewJobQuestionResult.processing(
                        idGenerator.generate(JOB_QUESTION_RESULT_ID_PREFIX),
                        job,
                        question,
                        question.getOriginalAnswer()
                ))
                .toList());
        // Job 상태를 PENDING -> PROCESSING 으로 변경
        job.startProcessing(STARTED_MESSAGE);
        return new ReviewWork(new ReviewRequest(
                coverLetter.getTitle(),
                coverLetter.getCompanyName(),
                coverLetter.getPositionTitle(),
                coverLetter.getJobPostingUrl(),
                coverLetter.getPreferences(),
                questions.stream()
                        .map(question -> new ReviewQuestion(
                                question.getId(),
                                question.getQuestionOrder(),
                                question.getQuestion(),
                                question.getMaxAnswerLength(),
                                question.getOriginalAnswer()
                        ))
                        .toList()
        ));
    }

    @Transactional
    public void fail(String jobId, ReviewClientException.Reason reason) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateFirstReviewJob(locked.job());
        if (job.getStatus().isTerminal()) {
            return;
        }

        CoverLetter coverLetter = locked.coverLetter();

        Instant now = Instant.now(clock);
        job.markFailed(
                job.getProgressCurrent(),
                FAILED_MESSAGE,
                errorCode(reason),
                errorMessage(reason),
                now
        );
        coverLetter.failReview(now);
    }

    @Transactional
    public void failUnexpected(String jobId) {
        fail(jobId, UNEXPECTED_ERROR_CODE, UNEXPECTED_ERROR_MESSAGE);
    }

    private void fail(String jobId, String errorCode, String errorMessage) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateFirstReviewJob(locked.job());
        if (job.getStatus().isTerminal()) {
            return;
        }
        CoverLetter coverLetter = locked.coverLetter();
        Instant now = Instant.now(clock);
        job.markFailed(job.getProgressCurrent(), FAILED_MESSAGE, errorCode, errorMessage, now);
        coverLetter.failReview(now);
    }

    private LlmJob validateFirstReviewJob(LlmJob job) {
        if (job.getType() != LlmJobType.COVER_LETTER_REVIEW
                || job.getTargetType() != LlmJobTargetType.COVER_LETTER) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return job;
    }

    private String errorCode(ReviewClientException.Reason reason) {
        if (reason == ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_CODE;
        }
        return PROVIDER_ERROR_CODE;
    }

    private String errorMessage(ReviewClientException.Reason reason) {
        if (reason == ReviewClientException.Reason.OUTPUT_VALIDATION_FAILED) {
            return OUTPUT_VALIDATION_ERROR_MESSAGE;
        }
        return PROVIDER_ERROR_MESSAGE;
    }
}
