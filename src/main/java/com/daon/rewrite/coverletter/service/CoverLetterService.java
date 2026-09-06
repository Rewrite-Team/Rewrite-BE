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
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.LlmJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String COVER_LETTER_ID_PREFIX = "cl";
    private static final int MAX_LIST_SIZE = 9;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_COMPANY_NAME_LENGTH = 30;
    private static final int MAX_POSITION_TITLE_LENGTH = 30;
    private static final int MAX_JOB_POSTING_URL_LENGTH = 500;
    private static final int MAX_PREFERENCES_LENGTH = 3000;
    private static final String COVER_LETTER_QUESTION_ID_PREFIX = "clq";
    private static final int MAX_QUESTION_LENGTH = 300;
    private static final int MIN_MAX_ANSWER_LENGTH = 100;
    private static final int MAX_MAX_ANSWER_LENGTH = 5000;
    private static final int MAX_ORIGINAL_ANSWER_LENGTH = 5000;
    private static final String LLM_JOB_ID_PREFIX = "job";
    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterQuestionRepository coverLetterQuestionRepository;
    private final LlmJobRepository llmJobRepository;
    private final LlmJobService llmJobService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CoverLetter create() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = CoverLetter.create(
                idGenerator.generate(COVER_LETTER_ID_PREFIX),
                currentUser.id(),
                Instant.now(clock)
        );

        return coverLetterRepository.save(coverLetter);
    }

    @Transactional(readOnly = true)
    public Page<CoverLetter> findMyCoverLetters(int page, int size) {
        validateListQuery(page, size);

        CurrentUser currentUser = currentUserProvider.currentUser();
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return coverLetterRepository.findByOwnerIdAndDeletedAtIsNull(
                currentUser.id(),
                pageable
        );
    }

    @Transactional
    public void deleteMyCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Instant now = Instant.now(clock);
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (runningJob != null) {
            runningJob.cancel(now);
        }
        coverLetter.markDeleted(now);
    }

    @Transactional
    public void saveBasicInfo(
            String coverLetterId,
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        BasicInfoInput input = validateAndNormalizeBasicInfo(
                title,
                companyName,
                positionTitle,
                jobPostingUrl
        );
        coverLetter.fillBasicInfo(
                input.title(),
                input.companyName(),
                input.positionTitle(),
                input.jobPostingUrl(),
                Instant.now(clock)
        );

    }

    @Transactional
    public void savePreferences(String coverLetterId, String preferences) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        String normalizedPreferences = validateAndNormalizePreferences(preferences);
        coverLetter.fillPreferences(normalizedPreferences, Instant.now(clock));

    }

    @Transactional
    public void saveQuestions(String coverLetterId, List<SaveQuestionInput> questions) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        List<NormalizedQuestionInput> normalizedQuestions = validateAndNormalizeQuestions(questions);
        // 해당 자기소개서에 기존에 저장돼 있던 문항들을 전부 삭제
        coverLetterQuestionRepository.deleteByCoverLetter(coverLetter);

        List<CoverLetterQuestion> savedQuestions = new ArrayList<>();
        for (int index = 0; index < normalizedQuestions.size(); index++) {
            NormalizedQuestionInput question = normalizedQuestions.get(index);
            savedQuestions.add(CoverLetterQuestion.create(
                    idGenerator.generate(COVER_LETTER_QUESTION_ID_PREFIX),
                    coverLetter,
                    index + 1,
                    question.question(),
                    question.maxAnswerLength(),
                    question.originalAnswer()
            ));
        }
        savedQuestions = coverLetterQuestionRepository.saveAll(savedQuestions);

        coverLetter.touch(Instant.now(clock));
    }

    /*
    자기소개서 제출을 처리하고, 필요하면 최초 AI 첨삭 Job 생성

    [성공 케이스 3가지]
    상황                      상태          반환Job
    최초 제출 또는 실패 후 재시도	 REVIEWING	  새 Job
    최초 첨삭 중 중복 제출	     REVIEWING	  기존 Job
    이미 최초 첨삭 완료	         REVIEWED	  null
     */
    @Transactional
    public SubmitCoverLetterResult submit(String coverLetterId) {
        // 현재 사용자 조회
        CurrentUser currentUser = currentUserProvider.currentUser();
        // 자기소개서 조회 (coverLetterId + 현재 사용자 소유 + 삭제x)
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 이미 진행 중인 LLM job(PENDING, PROCESSING) 조회
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());

        // 이미 Job이 진행 중이라면
        if (runningJob != null) {
            // 동일한 최초 첨삭 Job이 이미 진행 중이라면, 새 Job을 만들지 않고 기존 Job 반환
            // 버튼 중복 클릭, 네트워크 재시도에 대한 멱등 처리.
            if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING
                    && runningJob.getType() == LlmJobType.COVER_LETTER_REVIEW) {
                return new SubmitCoverLetterResult(coverLetter, runningJob);
            }
            // 다른 종류의 Job 이 진행중이라면, 409 Conflict 반환
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        // 이미 첨삭 결과가 있는 경우 이미 최초 첨삭이 완료된 것이기에 새 Job을 만들지 않는다.
        if (coverLetter.getLatestReviewedVersionId() != null) {
            // 성공 버전이 있는데 REVIEW_FAILED 라면, 최초 첨삭 API 가 아니라 재첨삭 API를 사용해야 하므로 CONFLICT 반환
            if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return new SubmitCoverLetterResult(coverLetter, null);
        }

        // 자기소개서는 REVIEWING인데 진행 중 Job이 발견되지 않은 비정상적인 상태 조합 방어
        if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 저장된 문항을 등록 순서대로 조회
        List<CoverLetterQuestion> questions = coverLetterQuestionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        // 제출 필수값 최종 검증
        validateSubmit(coverLetter, questions);

        Instant now = Instant.now(clock);
        /*
         아래 상태의 LlmJob 저장
         type: COVER_LETTER_REVIEW
         status: PENDING
         targetType: COVER_LETTER
         targetId: 자기소개서 ID
         progressCurrent: 0
         progressTotal: 문항 수
         maxAttempts: 2
         */
        LlmJob job = llmJobRepository.save(LlmJob.pendingReview(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                now,
                questions.size()
        ));

        /*
         자소서 상태를 REVIEWING으로 변경
         최초 제출이면 submittedAt 설정 및 updatedAt 갱신
         */
        coverLetter.startReview(now);

        /*
         * IMPROVE
         *  현재 구조에서는 LlmJobCreatedEvent가 애플리케이션 메모리 안에서만 전달된다. 따라서 커밋 직후 종료되면 인메모리 이벤트가 유실될 수 있다.
         *  서버 재시작 중 Job 유실 방지나 Worker 수평 확장이 필요해지는 시점에 "Outbox 패턴 + 메시지큐 + 독립 Worker" 구조로 전환 필요
         */
        // 비동기 첨삭을 시작하는 이벤트 발행 (ReviewJobEventListener.java의 리스너가 FirstReviewJobWorker.execute(jobId) 실행)
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        // AI 첨삭 완료를 기다리지 않고 REVIEWING과 jobId를 바로 반환
        return new SubmitCoverLetterResult(coverLetter, job);
    }

    // 제출 필수값 최종 검증
    // 기본 정보(제목, 회사명, 직무명, 우대사항), 문항 존재 여부,
    // 각 문항의 질문·원본 답변 입력 여부와 최대 답변 글자 수 범위(100~5000자) 확인
    // 오류가 여러 개라면 아래와같이 details에 모두 모아 반환
    /**
     * <pre>{@code
     *   "error": {
     *     "code": "VALIDATION_ERROR",
     *     "details": [
     *       {
     *         "field": "preferences",
     *         "reason": "채용 우대사항을 입력해야 합니다."
     *       },
     *       {
     *         "field": "questions[0].originalAnswer",
     *         "reason": "답변을 입력해야 합니다."
     *       }
     *     ]
     *   }
     * }</pre>
     */
    private void validateSubmit(CoverLetter coverLetter, List<CoverLetterQuestion> questions) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        addMissingDetail("title", coverLetter.getTitle(), "자기소개서 제목을 입력해야 합니다.", details);
        addMissingDetail("companyName", coverLetter.getCompanyName(), "회사명을 입력해야 합니다.", details);
        addMissingDetail("positionTitle", coverLetter.getPositionTitle(), "직무명을 입력해야 합니다.", details);
        addMissingDetail("preferences", coverLetter.getPreferences(), "채용 우대사항을 입력해야 합니다.", details);

        // 질문 답변 자체가 없는 경우
        if (questions.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "questions",
                    "질문과 답변을 1개 이상 입력해야 합니다."
            ));
        } else {
            for (int index = 0; index < questions.size(); index++) {
                CoverLetterQuestion question = questions.get(index);
                // 해당 index의 질문 유무 검사
                addMissingDetail(
                        "questions[" + index + "].question",
                        question.getQuestion(),
                        "질문을 입력해야 합니다.",
                        details
                );
                // 최대 답변 글자 수의 허용 범위 검사
                if (question.getMaxAnswerLength() == null
                        || question.getMaxAnswerLength() < MIN_MAX_ANSWER_LENGTH
                        || question.getMaxAnswerLength() > MAX_MAX_ANSWER_LENGTH) {
                    details.add(new ErrorResponse.ErrorDetail(
                            "questions[" + index + "].maxAnswerLength",
                            "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
                    ));
                }
                // 답변 유무 검사
                addMissingDetail(
                        "questions[" + index + "].originalAnswer",
                        question.getOriginalAnswer(),
                        "답변을 입력해야 합니다.",
                        details
                );
            }
        }

        // 에러가 적어도 1개 있다면 BusinessException 을 던짐
        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }
    }

    // 누락된 필드가 있다면 ErrorDetail에 추가
    private void addMissingDetail(
            String field,
            String value,
            String reason,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (value == null || value.isBlank()) {
            details.add(new ErrorResponse.ErrorDetail(field, reason));
        }
    }

    private void validateListQuery(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private BasicInfoInput validateAndNormalizeBasicInfo(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        String normalizedTitle = normalizeOptionalText(
                "title",
                title,
                MAX_TITLE_LENGTH,
                "자기소개서 제목은 최대 50자까지 입력할 수 있습니다.",
                details
        );
        String normalizedCompanyName = normalizeOptionalText(
                "companyName",
                companyName,
                MAX_COMPANY_NAME_LENGTH,
                "회사명은 최대 30자까지 입력할 수 있습니다.",
                details
        );
        String normalizedPositionTitle = normalizeOptionalText(
                "positionTitle",
                positionTitle,
                MAX_POSITION_TITLE_LENGTH,
                "직무명은 최대 30자까지 입력할 수 있습니다.",
                details
        );
        String normalizedJobPostingUrl = normalizeOptionalJobPostingUrl(jobPostingUrl, details);

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        return new BasicInfoInput(
                normalizedTitle,
                normalizedCompanyName,
                normalizedPositionTitle,
                normalizedJobPostingUrl
        );
    }

    private String normalizeOptionalText(
            String field,
            String value,
            int maxLength,
            String tooLongMessage,
            List<ErrorResponse.ErrorDetail> details
    ) {
        String normalized = normalize(value);
        if (normalized != null && countCodePoints(normalized) > maxLength) {
            details.add(new ErrorResponse.ErrorDetail(field, tooLongMessage));
        }
        return normalized;
    }

    private String validateAndNormalizePreferences(String preferences) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        String normalizedPreferences = normalizeOptionalText(
                "preferences",
                preferences,
                MAX_PREFERENCES_LENGTH,
                "채용 우대사항은 최대 3000자까지 입력할 수 있습니다.",
                details
        );

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        return normalizedPreferences;
    }

    private List<NormalizedQuestionInput> validateAndNormalizeQuestions(List<SaveQuestionInput> questions) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        if (questions == null) {
            return List.of();
        }

        List<NormalizedQuestionInput> normalizedQuestions = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            SaveQuestionInput question = questions.get(index);
            if (question == null) {
                details.add(new ErrorResponse.ErrorDetail(
                        "questions[" + index + "]",
                        "문항 정보를 입력해야 합니다."
                ));
                continue;
            }

            String normalizedQuestion = normalizeOptionalText(
                    "questions[" + index + "].question",
                    question.question(),
                    MAX_QUESTION_LENGTH,
                    "질문은 최대 300자까지 입력할 수 있습니다.",
                    details
            );
            validateMaxAnswerLength(index, question.maxAnswerLength(), details);
            String normalizedOriginalAnswer = normalizeOptionalText(
                    "questions[" + index + "].originalAnswer",
                    question.originalAnswer(),
                    MAX_ORIGINAL_ANSWER_LENGTH,
                    "답변은 최대 5000자까지 입력할 수 있습니다.",
                    details
            );

            normalizedQuestions.add(new NormalizedQuestionInput(
                    normalizedQuestion,
                    question.maxAnswerLength(),
                    normalizedOriginalAnswer
            ));
        }

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        return normalizedQuestions;
    }

    private void validateMaxAnswerLength(
            int index,
            Integer maxAnswerLength,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (maxAnswerLength != null
                && (maxAnswerLength < MIN_MAX_ANSWER_LENGTH
                || maxAnswerLength > MAX_MAX_ANSWER_LENGTH)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "questions[" + index + "].maxAnswerLength",
                    "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
            ));
        }
    }

    private String normalizeOptionalJobPostingUrl(
            String value,
            List<ErrorResponse.ErrorDetail> details
    ) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (countCodePoints(normalized) > MAX_JOB_POSTING_URL_LENGTH) {
            details.add(new ErrorResponse.ErrorDetail(
                    "jobPostingUrl",
                    "공고 링크는 최대 500자까지 입력할 수 있습니다."
            ));
        }
        if (!isAbsoluteUrl(normalized)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "jobPostingUrl",
                    "공고 링크 형식이 올바르지 않습니다."
            ));
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private boolean isAbsoluteUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute() && uri.getScheme() != null && !uri.getScheme().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private record BasicInfoInput(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
    }

    private record NormalizedQuestionInput(
            String question,
            Integer maxAnswerLength,
            String originalAnswer
    ) {
    }
}
