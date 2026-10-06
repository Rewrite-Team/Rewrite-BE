package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Component
class CoverLetterInputPolicy {

    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_COMPANY_NAME_LENGTH = 30;
    private static final int MAX_POSITION_TITLE_LENGTH = 30;
    private static final int MAX_JOB_POSTING_URL_LENGTH = 500;
    private static final int MAX_PREFERENCES_LENGTH = 3000;
    private static final int MAX_QUESTION_LENGTH = 300;
    private static final int MIN_MAX_ANSWER_LENGTH = 100;
    private static final int MAX_MAX_ANSWER_LENGTH = 5000;
    private static final int MAX_ORIGINAL_ANSWER_LENGTH = 5000;

    BasicInfo normalizeBasicInfo(
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

        return new BasicInfo(
                normalizedTitle,
                normalizedCompanyName,
                normalizedPositionTitle,
                normalizedJobPostingUrl
        );
    }

    String normalizePreferences(String preferences) {
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

    List<SaveQuestionInput> normalizeQuestions(List<SaveQuestionInput> questions) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        if (questions == null) {
            return List.of();
        }

        List<SaveQuestionInput> normalizedQuestions = new ArrayList<>();
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

            normalizedQuestions.add(new SaveQuestionInput(
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
    void validateSubmit(CoverLetter coverLetter, List<CoverLetterQuestion> questions) {
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

    record BasicInfo(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
    }
}
