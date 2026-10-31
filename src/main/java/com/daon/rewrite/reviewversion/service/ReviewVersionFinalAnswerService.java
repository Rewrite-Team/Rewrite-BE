package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 최신 성공 버전의 최종 작성본 저장을 처리한다.
 * 자기소개서 잠금 안에서 버전을 확인해 저장과 재첨삭 입력 확정의 순서를 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class ReviewVersionFinalAnswerService {

    private static final int MAX_FINAL_ANSWER_LENGTH = 5000;

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;

    /**
     * 요청한 버전이 저장 시점에도 최신 성공 버전인지 확인하고 모든 문항의 최종 작성본을 함께 갱신한다.
     * 전체 입력의 누락·중복·소속과 문자열을 먼저 검증해 부분 저장을 막으며 새 버전도 만들지 않는다.
     */
    @Transactional
    public void saveMyFinalAnswers(
            String coverLetterId,
            String versionId,
            List<SaveFinalAnswerInput> inputs
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ReviewVersion reviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!reviewVersion.getId().equals(coverLetter.getLatestReviewedVersionId())
                || reviewVersion.getStatus() != LlmJobStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REVIEW_VERSION_NOT_LATEST);
        }

        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());
        Map<String, String> normalizedAnswers = validateAndNormalize(questionResults, inputs);

        for (ReviewVersionQuestionResult questionResult : questionResults) {
            questionResult.updateFinalAnswer(normalizedAnswers.get(questionResult.getId()));
        }

    }

    /**
     * 입력 배열이 해당 버전의 모든 문항 결과를 정확히 한 번씩 포함하는지 확인한다.
     * 답변은 앞뒤 공백 제거 후 1~5000 code point로 검증하며 문항별 오류를 모아 예외에 담는다.
     */
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

        Set<String> seenIds = new HashSet<>();
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

            boolean validId = validateQuestionResultId(index, input.questionResultId(), expectedIds, seenIds, details);
            String normalizedFinalAnswer = validateFinalAnswer(index, input.finalAnswer(), details);
            if (validId) {
                seenIds.add(input.questionResultId());
                if (normalizedFinalAnswer != null) {
                    normalizedAnswers.put(input.questionResultId(), normalizedFinalAnswer);
                }
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

    private boolean validateQuestionResultId(
            int index,
            String questionResultId,
            Set<String> expectedIds,
            Set<String> seenIds,
            List<ErrorResponse.ErrorDetail> details
    ) {
        if (questionResultId == null || questionResultId.isBlank()) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "문항 결과 ID는 필수입니다."
            ));
            return false;
        }
        if (!expectedIds.contains(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "첨삭 버전에 포함되지 않은 문항 결과입니다."
            ));
            return false;
        }
        if (seenIds.contains(questionResultId)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "answers[" + index + "].questionResultId",
                    "중복된 문항 결과입니다."
            ));
            return false;
        }
        return true;
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
