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

/**
 * 최초 첨삭의 입력 준비·PROCESSING 전환과 실패 상태 저장을 짧은 트랜잭션으로 수행한다.
 * 자기소개서와 Job을 잠근 뒤 상태를 확인해 중복 시작과 취소 후 상태 덮어쓰기를 막는다.
 * 성공한 임시 문항 결과와 요청 시 생성한 버전은 전체 Job이 실패해도 보존한다.
 */
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

    /**
     * 원문 답변을 문항별 임시 입력에 저장하고 외부 호출에 필요한 값만 반환한다.
     * 입력 준비와 PROCESSING 전환은 함께 커밋되며, 이미 시작했거나 종료된 Job이면 null을 반환한다.
     */
    @Transactional
    public ReviewWork start(String jobId) {
        CoverLetterJobLockService.LockedCoverLetterJob locked = coverLetterJobLockService.lock(jobId);
        LlmJob job = validateFirstReviewJob(locked.job());
        if (job.getStatus() != LlmJobStatus.PENDING) {
            return null;
        }

        CoverLetter coverLetter = locked.coverLetter();
        if (coverLetter.getStatus() != CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        List<CoverLetterQuestion> questions = questionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 최초 첨삭은 처리 시작 시 originalAnswer와 문항 정보를 고정해 진행·실패 화면에서도 실제 입력을 복구한다.
        jobQuestionResultRepository.saveAll(questions.stream()
                .map(question -> ReviewJobQuestionResult.processing(
                        idGenerator.generate(JOB_QUESTION_RESULT_ID_PREFIX),
                        job,
                        question,
                        question.getOriginalAnswer()
                ))
                .toList());
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

    /** 완료 문항 수를 유지한 채 Job·자기소개서를 함께 실패로 전환한다. 이미 종료된 Job은 변경하지 않는다. */
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

    /** 분류된 LLM 실패 외의 오류를 내부 오류로 기록하며, 이미 확정된 종료 상태는 유지한다. */
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
