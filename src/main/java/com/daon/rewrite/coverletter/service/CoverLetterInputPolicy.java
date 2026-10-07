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

/**
 * 등록 step 저장의 입력 정규화·값 검증과 제출의 필수값 검증을 나누어 관리한다.
 * 임시저장은 앞뒤 공백과 빈 입력을 정리한 뒤 입력된 값의 길이·범위·형식만 검사한다.
 * 제출은 저장된 기본 정보·우대사항·문항이 완성됐는지 확인하고 필드별 오류를 모아 반환한다.
 */
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

    /** 기본 정보 전체 폼의 미입력값을 null로 정규화하고 값이 있는 필드의 오류를 한 번에 수집한다. */
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

    /**
     * 문항 필드의 미입력은 허용하며, 목록 null은 전체 교체할 빈 목록으로 정규화한다.
     * 배열 안의 null 문항과 입력된 값의 길이·범위 오류는 questions[index] 경로로 모아 거부한다.
     */
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

    /**
     * Job 생성 전에 저장된 제목·회사·직무·우대사항과 1개 이상 문항의 필수값을 최종 확인한다.
     * 각 문항은 질문·답변과 허용 범위의 최대 글자 수가 필요하며, 공고 링크는 필수값에 포함하지 않는다.
     * 여러 누락을 details에 함께 모으고 문항은 저장 순서의 0부터 시작하는 index로 필드를 식별한다.
     */
    void validateSubmit(CoverLetter coverLetter, List<CoverLetterQuestion> questions) {
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

    /** 링크의 길이와 scheme이 있는 절대 URI 형식만 확인하며, 외부 사이트에 접속하지 않는다. */
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

    // UTF-16 char 수 대신 Unicode code point 수를 사용해 보조 평면 문자를 두 글자로 세지 않는다.
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
