package com.daon.rewrite.coverletter;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse.ErrorDetail;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.job.FirstReviewJobWorker;
import com.daon.rewrite.reviewversion.job.ReReviewJobWorker;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class CoverLetterSubmitValidationIntegrationTest {

    @Autowired private CoverLetterService coverLetterService;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private CoverLetterQuestionRepository questionRepository;
    @Autowired private LlmJobRepository jobRepository;
    @Autowired private ReviewVersionRepository versionRepository;

    @MockitoBean private FirstReviewJobWorker firstReviewJobWorker;
    @MockitoBean private ReReviewJobWorker reReviewJobWorker;

    @Test
    void unfinishedStepsCanBeSavedButSubmitReportsEveryRequiredFieldInOrder() {
        String id = coverLetterService.create().getId();
        coverLetterService.saveBasicInfo(id, null, " \t", "", null);
        coverLetterService.savePreferences(id, " \n");
        coverLetterService.saveQuestions(id, null);
        CoverLetter before = coverLetterRepository.findById(id).orElseThrow();

        assertThat(before.getTitle()).isNull();
        assertThat(before.getCompanyName()).isNull();
        assertThat(before.getPositionTitle()).isNull();
        assertThat(before.getPreferences()).isNull();
        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(id)).isEmpty();
        assertThatThrownBy(() -> coverLetterService.submit(id))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails()).containsExactly(
                            new ErrorDetail("title", "자기소개서 제목을 입력해야 합니다."),
                            new ErrorDetail("companyName", "회사명을 입력해야 합니다."),
                            new ErrorDetail("positionTitle", "직무명을 입력해야 합니다."),
                            new ErrorDetail("preferences", "채용 우대사항을 입력해야 합니다."),
                            new ErrorDetail("questions", "질문과 답변을 1개 이상 입력해야 합니다.")
                    );
                });

        assertNoSubmissionSideEffects(before);
        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(id)).isEmpty();
    }

    @Test
    void submitCollectsBasicAndQuestionDetailsWithoutChangingSavedQuestions() {
        String id = coverLetterService.create().getId();
        coverLetterService.saveBasicInfo(id, null, "회사", "개발자", "https://example.com/jobs/1");
        coverLetterService.savePreferences(id, "서버 개발 경험");
        coverLetterService.saveQuestions(id, List.of(
                new SaveQuestionInput(" \t", null, null),
                new SaveQuestionInput("두 번째 질문", 100, " \n")
        ));
        CoverLetter before = coverLetterRepository.findById(id).orElseThrow();
        List<String> questionIds = questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(id)
                .stream().map(CoverLetterQuestion::getId).toList();

        assertThatThrownBy(() -> coverLetterService.submit(id))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails()).containsExactly(
                            new ErrorDetail("title", "자기소개서 제목을 입력해야 합니다."),
                            new ErrorDetail("questions[0].question", "질문을 입력해야 합니다."),
                            new ErrorDetail("questions[0].maxAnswerLength",
                                    "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."),
                            new ErrorDetail("questions[0].originalAnswer", "답변을 입력해야 합니다."),
                            new ErrorDetail("questions[1].originalAnswer", "답변을 입력해야 합니다.")
                    );
                });

        assertNoSubmissionSideEffects(before);
        List<CoverLetterQuestion> saved = questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(id);
        assertThat(saved).extracting(CoverLetterQuestion::getId).containsExactlyElementsOf(questionIds);
        assertThat(saved).extracting(CoverLetterQuestion::getQuestionOrder, CoverLetterQuestion::getQuestion,
                        CoverLetterQuestion::getMaxAnswerLength, CoverLetterQuestion::getOriginalAnswer)
                .containsExactly(tuple(1, null, null, null), tuple(2, "두 번째 질문", 100, null));
    }

    @Test
    void submitAllowsMissingJobPostingUrlAndAnswerLongerThanQuestionLimit() {
        String id = coverLetterService.create().getId();
        String originalAnswer = "답".repeat(5000);
        coverLetterService.saveBasicInfo(id, "지원 자기소개서", "회사", "개발자", null);
        coverLetterService.savePreferences(id, "서버 개발 경험");
        coverLetterService.saveQuestions(id, List.of(
                new SaveQuestionInput("지원 동기는 무엇인가요?", 100, originalAnswer)
        ));

        var result = coverLetterService.submit(id);

        assertThat(result.coverLetter().getStatus()).isEqualTo(CoverLetterStatus.REVIEWING);
        assertThat(result.coverLetter().getJobPostingUrl()).isNull();
        assertThat(result.job().getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(versionRepository.countByCoverLetterId(id)).isEqualTo(1);
        CoverLetterQuestion saved = questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(id).getFirst();
        assertThat(saved.getMaxAnswerLength()).isEqualTo(100);
        assertThat(saved.getOriginalAnswer()).isEqualTo(originalAnswer);
    }

    @Test
    void stepSavesCheckOwnershipDeletionAndWritingStatusBeforeInvalidInput() {
        CoverLetter foreign = coverLetterRepository.save(CoverLetter.create(
                "cl_foreign_" + UUID.randomUUID(), "another_user", Instant.parse("2026-01-01T00:00:00Z")
        ));
        assertThatThrownBy(() -> coverLetterService.saveBasicInfo(
                foreign.getId(), "제".repeat(51), "회사", "개발자", "relative/path"
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
            assertThat(exception.getDetails()).isEmpty();
        });
        assertThat(coverLetterRepository.findById(foreign.getId()).orElseThrow().getTitle()).isNull();

        String deletedId = coverLetterService.create().getId();
        coverLetterService.deleteMyCoverLetter(deletedId);
        CoverLetter deleted = coverLetterRepository.findById(deletedId).orElseThrow();
        assertThatThrownBy(() -> coverLetterService.savePreferences(deletedId, "우".repeat(3001)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(exception.getDetails()).isEmpty();
                });
        CoverLetter stillDeleted = coverLetterRepository.findById(deletedId).orElseThrow();
        assertThat(stillDeleted.getPreferences()).isNull();
        assertThat(stillDeleted.getDeletedAt()).isEqualTo(deleted.getDeletedAt());
        assertThat(stillDeleted.getUpdatedAt()).isEqualTo(deleted.getUpdatedAt());

        String reviewingId = coverLetterService.create().getId();
        coverLetterService.saveQuestions(reviewingId, List.of(new SaveQuestionInput("기존 질문", 100, "기존 답변")));
        CoverLetter reviewing = coverLetterRepository.findById(reviewingId).orElseThrow();
        reviewing.startReview(Instant.now());
        coverLetterRepository.save(reviewing);
        assertThatThrownBy(() -> coverLetterService.saveQuestions(reviewingId, List.of(
                new SaveQuestionInput("질".repeat(301), 99, "답".repeat(5001))
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COVER_LETTER_NOT_WRITING);
            assertThat(exception.getDetails()).isEmpty();
        });
        assertThat(coverLetterRepository.findById(reviewingId).orElseThrow().getStatus())
                .isEqualTo(CoverLetterStatus.REVIEWING);
        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(reviewingId))
                .extracting(CoverLetterQuestion::getQuestion, CoverLetterQuestion::getMaxAnswerLength,
                        CoverLetterQuestion::getOriginalAnswer)
                .containsExactly(tuple("기존 질문", 100, "기존 답변"));
    }

    private void assertNoSubmissionSideEffects(CoverLetter before) {
        CoverLetter after = coverLetterRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(CoverLetterStatus.WRITING);
        assertThat(after.getTitle()).isEqualTo(before.getTitle());
        assertThat(after.getCompanyName()).isEqualTo(before.getCompanyName());
        assertThat(after.getPositionTitle()).isEqualTo(before.getPositionTitle());
        assertThat(after.getJobPostingUrl()).isEqualTo(before.getJobPostingUrl());
        assertThat(after.getPreferences()).isEqualTo(before.getPreferences());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getSubmittedAt()).isNull();
        assertThat(after.getLatestReviewedVersionId()).isNull();
        assertThat(jobRepository.findFirstByTargetTypeAndTargetIdAndTypeOrderByCreatedAtDescIdDesc(
                LlmJobTargetType.COVER_LETTER, before.getId(), LlmJobType.COVER_LETTER_REVIEW
        )).isEmpty();
        assertThat(versionRepository.countByCoverLetterId(before.getId())).isZero();
    }
}
