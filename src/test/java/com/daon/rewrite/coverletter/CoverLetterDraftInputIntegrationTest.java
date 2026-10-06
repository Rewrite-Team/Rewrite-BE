package com.daon.rewrite.coverletter;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse.ErrorDetail;
import com.daon.rewrite.reviewversion.job.FirstReviewJobWorker;
import com.daon.rewrite.reviewversion.job.ReReviewJobWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CoverLetterDraftInputIntegrationTest {

    @Autowired private CoverLetterService coverLetterService;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private CoverLetterQuestionRepository questionRepository;

    @MockitoBean private FirstReviewJobWorker firstReviewJobWorker;
    @MockitoBean private ReReviewJobWorker reReviewJobWorker;

    @Test
    void partialDraftStripsWhitespaceAndReplacesMissingValuesWithNull() {
        String coverLetterId = coverLetterService.create().getId();
        coverLetterService.saveBasicInfo(coverLetterId, "이전 제목", "이전 회사", "이전 직무", "https://example.com");

        coverLetterService.saveBasicInfo(coverLetterId, " \t지원😀\u2003", null, " \t\u2003", " \t\u2003");
        coverLetterService.savePreferences(coverLetterId, " \t개발 경험😀\u2003");
        coverLetterService.saveQuestions(coverLetterId, List.of(
                new SaveQuestionInput(" \t질문😀\u2003", null, " \t\u2003"),
                new SaveQuestionInput(null, 1000, " \t답변😀\u2003")
        ));

        CoverLetter saved = coverLetterRepository.findById(coverLetterId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CoverLetterStatus.WRITING);
        assertThat(saved.getTitle()).isEqualTo("지원😀");
        assertThat(saved.getCompanyName()).isNull();
        assertThat(saved.getPositionTitle()).isNull();
        assertThat(saved.getJobPostingUrl()).isNull();
        assertThat(saved.getPreferences()).isEqualTo("개발 경험😀");
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::question)
                .containsExactly("질문😀", null);
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::maxAnswerLength)
                .containsExactly(null, 1000);
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::originalAnswer)
                .containsExactly(null, "답변😀");

        coverLetterService.savePreferences(coverLetterId, null);

        assertThat(coverLetterRepository.findById(coverLetterId).orElseThrow().getPreferences()).isNull();
    }

    @Test
    void basicInfoAcceptsStrippedLengthLimitsAndCollectsOverLimitFieldsInOrder() {
        String coverLetterId = coverLetterService.create().getId();
        String title = "t".repeat(50);
        String company = "c".repeat(30);
        String position = "p".repeat(30);
        coverLetterService.saveBasicInfo(coverLetterId,
                " \t" + title + "\u2003", " \t" + company + "\u2003", " \t" + position + "\u2003", null);
        CoverLetter saved = coverLetterRepository.findById(coverLetterId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo(title);
        assertThat(saved.getCompanyName()).isEqualTo(company);
        assertThat(saved.getPositionTitle()).isEqualTo(position);
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.saveBasicInfo(coverLetterId,
                        " " + title + "t ", " " + company + "c ", " " + position + "p ", null),
                new ErrorDetail("title", "자기소개서 제목은 최대 50자까지 입력할 수 있습니다."),
                new ErrorDetail("companyName", "회사명은 최대 30자까지 입력할 수 있습니다."),
                new ErrorDetail("positionTitle", "직무명은 최대 30자까지 입력할 수 있습니다."));

        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
    }

    @Test
    void preferencesUseUnicodeCodePointLengthAndPreserveSavedValueOnFailure() {
        String coverLetterId = coverLetterService.create().getId();
        String preferences = "😀".repeat(3000);
        coverLetterService.savePreferences(coverLetterId, " \t" + preferences + "\u2003");
        assertThat(coverLetterRepository.findById(coverLetterId).orElseThrow().getPreferences())
                .isEqualTo(preferences);
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.savePreferences(coverLetterId,
                        " \t" + preferences + "😀\u2003"),
                new ErrorDetail("preferences", "채용 우대사항은 최대 3000자까지 입력할 수 있습니다."));

        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
        coverLetterService.savePreferences(coverLetterId, " \t\u2003");
        assertThat(coverLetterRepository.findById(coverLetterId).orElseThrow().getPreferences()).isNull();
    }

    @Test
    void questionAndAnswerLengthLimitsAreCheckedAfterStripWithoutReplacingRowsOnFailure() {
        String coverLetterId = coverLetterService.create().getId();
        String question = "q".repeat(300);
        String answer = "😀".repeat(5000);
        coverLetterService.saveQuestions(coverLetterId, List.of(
                new SaveQuestionInput(" \t" + question + "\u2003", 5000, " \t" + answer + "\u2003")
        ));
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::question).containsExactly(question);
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::originalAnswer).containsExactly(answer);
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.saveQuestions(coverLetterId, List.of(
                        new SaveQuestionInput(" " + question + "q ", 5000, " " + answer + "😀 "))),
                new ErrorDetail("questions[0].question", "질문은 최대 300자까지 입력할 수 있습니다."),
                new ErrorDetail("questions[0].originalAnswer", "답변은 최대 5000자까지 입력할 수 있습니다."));

        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
    }

    @Test
    void maxAnswerLengthAcceptsBothLimitsAndRejectsValuesOutsideThem() {
        String coverLetterId = coverLetterService.create().getId();
        coverLetterService.saveQuestions(coverLetterId, List.of(
                new SaveQuestionInput("질문 1", 100, "a".repeat(101)),
                new SaveQuestionInput("질문 2", 5000, "답변 2")
        ));
        assertThat(storedQuestions(coverLetterId)).extracting(StoredQuestion::maxAnswerLength)
                .containsExactly(100, 5000);
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.saveQuestions(coverLetterId, List.of(
                        new SaveQuestionInput("변경 질문 1", 99, "변경 답변 1"),
                        new SaveQuestionInput("변경 질문 2", 5001, "변경 답변 2"))),
                new ErrorDetail("questions[0].maxAnswerLength", "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."),
                new ErrorDetail("questions[1].maxAnswerLength", "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."));

        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
    }

    @Test
    void savingQuestionsReplacesAllPreviousRowsInRequestOrder() {
        String coverLetterId = coverLetterService.create().getId();
        coverLetterService.saveQuestions(coverLetterId, List.of(
                new SaveQuestionInput("이전 질문 1", 1000, "이전 답변 1"),
                new SaveQuestionInput("이전 질문 2", 1000, "이전 답변 2")
        ));
        List<String> oldIds = storedQuestions(coverLetterId).stream().map(StoredQuestion::id).toList();

        coverLetterService.saveQuestions(coverLetterId, List.of(
                new SaveQuestionInput(" 새 질문 2 ", 2000, " 새 답변 2 "),
                new SaveQuestionInput(" 새 질문 1 ", 1000, " 새 답변 1 ")
        ));

        List<StoredQuestion> saved = storedQuestions(coverLetterId);
        assertThat(saved).extracting(StoredQuestion::order).containsExactly(1, 2);
        assertThat(saved).extracting(StoredQuestion::question).containsExactly("새 질문 2", "새 질문 1");
        assertThat(saved).extracting(StoredQuestion::originalAnswer).containsExactly("새 답변 2", "새 답변 1");
        assertThat(saved).extracting(StoredQuestion::id).doesNotContainAnyElementsOf(oldIds);
        assertThat(questionRepository.findAllById(oldIds)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyQuestionListDeletesAllStoredQuestions(List<SaveQuestionInput> inputs) {
        String coverLetterId = coverLetterService.create().getId();
        coverLetterService.saveQuestions(coverLetterId, List.of(new SaveQuestionInput("기존 질문", 1000, "기존 답변")));
        List<String> oldIds = storedQuestions(coverLetterId).stream().map(StoredQuestion::id).toList();

        coverLetterService.saveQuestions(coverLetterId, inputs);

        assertThat(storedQuestions(coverLetterId)).isEmpty();
        assertThat(questionRepository.findAllById(oldIds)).isEmpty();
    }

    @Test
    void nullQuestionElementAndOtherErrorsAreCollectedInOrderWithoutDeletingSavedRows() {
        String coverLetterId = coverLetterService.create().getId();
        coverLetterService.saveQuestions(coverLetterId, List.of(new SaveQuestionInput("기존 질문", 1000, "기존 답변")));
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.saveQuestions(coverLetterId, Arrays.asList(
                        null,
                        new SaveQuestionInput("q".repeat(301), 99, "a".repeat(5001)),
                        new SaveQuestionInput(null, null, null))),
                new ErrorDetail("questions[0]", "문항 정보를 입력해야 합니다."),
                new ErrorDetail("questions[1].question", "질문은 최대 300자까지 입력할 수 있습니다."),
                new ErrorDetail("questions[1].maxAnswerLength", "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."),
                new ErrorDetail("questions[1].originalAnswer", "답변은 최대 5000자까지 입력할 수 있습니다."));

        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
    }

    @Test
    void absoluteNonHttpUriIsAcceptedAndInvalidOrLongUrisKeepSavedBasicInfo() {
        String coverLetterId = coverLetterService.create().getId();
        String url = "mailto:" + "a".repeat(493);
        coverLetterService.saveBasicInfo(coverLetterId, "제목", "회사", "직무", " \t" + url + "\u2003");
        assertThat(coverLetterRepository.findById(coverLetterId).orElseThrow().getJobPostingUrl()).isEqualTo(url);
        StoredDraft before = storedDraft(coverLetterId);

        assertValidationError(() -> coverLetterService.saveBasicInfo(coverLetterId,
                        "변경 제목", "변경 회사", "변경 직무", "relative/path"),
                new ErrorDetail("jobPostingUrl", "공고 링크 형식이 올바르지 않습니다."));
        assertThat(storedDraft(coverLetterId)).isEqualTo(before);

        assertValidationError(() -> coverLetterService.saveBasicInfo(coverLetterId,
                        "변경 제목", "변경 회사", "변경 직무", url + "a"),
                new ErrorDetail("jobPostingUrl", "공고 링크는 최대 500자까지 입력할 수 있습니다."));
        assertThat(storedDraft(coverLetterId)).isEqualTo(before);

        assertValidationError(() -> coverLetterService.saveBasicInfo(coverLetterId,
                        "변경 제목", "변경 회사", "변경 직무", "a".repeat(501)),
                new ErrorDetail("jobPostingUrl", "공고 링크는 최대 500자까지 입력할 수 있습니다."),
                new ErrorDetail("jobPostingUrl", "공고 링크 형식이 올바르지 않습니다."));
        assertThat(storedDraft(coverLetterId)).isEqualTo(before);
    }

    private void assertValidationError(Runnable action, ErrorDetail... expectedDetails) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails()).containsExactly(expectedDetails);
                });
    }

    private List<StoredQuestion> storedQuestions(String coverLetterId) {
        return questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(coverLetterId).stream()
                .map(question -> new StoredQuestion(
                        question.getId(), question.getQuestionOrder(), question.getQuestion(),
                        question.getMaxAnswerLength(), question.getOriginalAnswer()
                ))
                .toList();
    }

    private StoredDraft storedDraft(String coverLetterId) {
        CoverLetter coverLetter = coverLetterRepository.findById(coverLetterId).orElseThrow();
        return new StoredDraft(
                coverLetter.getTitle(), coverLetter.getCompanyName(), coverLetter.getPositionTitle(),
                coverLetter.getJobPostingUrl(), coverLetter.getPreferences(), coverLetter.getStatus(),
                coverLetter.getUpdatedAt(), storedQuestions(coverLetterId)
        );
    }

    private record StoredQuestion(String id, int order, String question, Integer maxAnswerLength, String originalAnswer) {
    }

    private record StoredDraft(
            String title, String companyName, String positionTitle, String jobPostingUrl, String preferences,
            CoverLetterStatus status, Instant updatedAt, List<StoredQuestion> questions
    ) {
    }
}
