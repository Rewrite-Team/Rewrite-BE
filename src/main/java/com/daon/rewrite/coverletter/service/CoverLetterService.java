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

    @Transactional
    public SubmitCoverLetterResult submit(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (runningJob != null) {
            if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING
                    && runningJob.getType() == LlmJobType.COVER_LETTER_REVIEW) {
                return new SubmitCoverLetterResult(coverLetter, runningJob);
            }
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }
        if (coverLetter.getLatestReviewedVersionId() != null) {
            if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return new SubmitCoverLetterResult(coverLetter, null);
        }
        if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        List<CoverLetterQuestion> questions = coverLetterQuestionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        validateSubmit(coverLetter, questions);

        Instant now = Instant.now(clock);
        LlmJob job = llmJobRepository.save(LlmJob.pendingReview(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                now,
                questions.size()
        ));
        coverLetter.startReview(now);
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new SubmitCoverLetterResult(coverLetter, job);
    }

    private void validateSubmit(CoverLetter coverLetter, List<CoverLetterQuestion> questions) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        addMissingDetail("title", coverLetter.getTitle(), "자기소개서 제목을 입력해야 합니다.", details);
        addMissingDetail("companyName", coverLetter.getCompanyName(), "회사명을 입력해야 합니다.", details);
        addMissingDetail("positionTitle", coverLetter.getPositionTitle(), "직무명을 입력해야 합니다.", details);
        addMissingDetail("preferences", coverLetter.getPreferences(), "채용 우대사항을 입력해야 합니다.", details);

        if (questions.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "questions",
                    "질문과 답변을 1개 이상 입력해야 합니다."
            ));
        } else {
            for (int index = 0; index < questions.size(); index++) {
                CoverLetterQuestion question = questions.get(index);
                addMissingDetail(
                        "questions[" + index + "].question",
                        question.getQuestion(),
                        "질문을 입력해야 합니다.",
                        details
                );
                if (question.getMaxAnswerLength() == null
                        || question.getMaxAnswerLength() < MIN_MAX_ANSWER_LENGTH
                        || question.getMaxAnswerLength() > MAX_MAX_ANSWER_LENGTH) {
                    details.add(new ErrorResponse.ErrorDetail(
                            "questions[" + index + "].maxAnswerLength",
                            "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
                    ));
                }
                addMissingDetail(
                        "questions[" + index + "].originalAnswer",
                        question.getOriginalAnswer(),
                        "답변을 입력해야 합니다.",
                        details
                );
            }
        }

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }
    }

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
