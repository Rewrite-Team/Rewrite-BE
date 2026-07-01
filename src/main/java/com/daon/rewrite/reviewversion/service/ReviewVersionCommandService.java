package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewVersionCommandService {

    private static final int MAX_FINAL_ANSWER_LENGTH = 5000;

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;
    private final Clock clock;

    @Transactional
    public SaveFinalAnswersResult saveMyFinalAnswers(
            String coverLetterId,
            String versionId,
            List<SaveFinalAnswerInput> inputs
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ReviewVersion reviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!reviewVersion.getId().equals(coverLetter.getLatestReviewVersionId())) {
            throw new BusinessException(ErrorCode.REVIEW_VERSION_NOT_LATEST);
        }

        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());
        Map<String, String> normalizedAnswers = validateAndNormalize(questionResults, inputs);

        for (ReviewVersionQuestionResult questionResult : questionResults) {
            questionResult.updateFinalAnswer(normalizedAnswers.get(questionResult.getId()));
        }

        Instant updatedAt = Instant.now(clock);
        return new SaveFinalAnswersResult(
                coverLetter.getId(),
                reviewVersion.getId(),
                questionResults,
                updatedAt
        );
    }

    private Map<String, String> validateAndNormalize(
            List<ReviewVersionQuestionResult> questionResults,
            List<SaveFinalAnswerInput> inputs
    ) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        if (questionResults.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (inputs == null || inputs.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers",
                    "최종 작성본은 모든 문항을 포함해야 합니다."
            ));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        Set<String> expectedIds = new HashSet<>();
        for (ReviewVersionQuestionResult questionResult : questionResults) {
            expectedIds.add(questionResult.getId());
        }

        Map<String, String> normalizedAnswers = new HashMap<>();
        for (int index = 0; index < inputs.size(); index++) {
            SaveFinalAnswerInput input = inputs.get(index);
            if (input == null) {
                details.add(new ErrorResponse.ErrorDetail(
                        "answers[" + index + "]",
                        "최종 작성본 정보를 입력해야 합니다."
                ));
                continue;
            }

            validateQuestionResultId(index, input.questionResultId(), expectedIds, normalizedAnswers, details);
            String normalizedFinalAnswer = validateFinalAnswer(index, input.finalAnswer(), details);
            if (input.questionResultId() != null && normalizedFinalAnswer != null) {
                normalizedAnswers.putIfAbsent(input.questionResultId(), normalizedFinalAnswer);
            }
        }

        if (normalizedAnswers.size() != questionResults.size()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers",
                    "첨삭 버전에 포함된 모든 문항의 최종 작성본을 입력해야 합니다."
            ));
        }

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }
        return normalizedAnswers;
    }

    private void validateQuestionResultId(
            int index,
            String questionResultId,
            Set<String> expectedIds,
            Map<String, String> normalizedAnswers,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (questionResultId == null || questionResultId.isBlank()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "문항 결과 ID는 필수입니다."
            ));
            return;
        }
        if (!expectedIds.contains(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "첨삭 버전에 포함되지 않은 문항 결과입니다."
            ));
            return;
        }
        if (normalizedAnswers.containsKey(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "중복된 문항 결과입니다."
            ));
        }
    }

    private String validateFinalAnswer(
            int index,
            String finalAnswer,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (finalAnswer == null) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본을 입력해야 합니다."
            ));
            return null;
        }

        String normalized = finalAnswer.strip();
        if (normalized.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본을 입력해야 합니다."
            ));
            return null;
        }
        if (countCodePoints(normalized) > MAX_FINAL_ANSWER_LENGTH) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].finalAnswer",
                    "최종 작성본은 최대 5000자까지 입력할 수 있습니다."
            ));
        }
        return normalized;
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
