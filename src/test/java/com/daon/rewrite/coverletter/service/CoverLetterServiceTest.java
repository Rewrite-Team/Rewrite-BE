package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class CoverLetterServiceTest {

    @Autowired
    private CoverLetterService service;

    @Autowired
    private CoverLetterRepository repository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        questionRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void createDraftPersistsCurrentUserOwnedDraft() {
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("cl")).willReturn("cl_fixed");
        given(clock.instant()).willReturn(now);
        given(clock.getZone()).willReturn(seoulZone);

        CoverLetter result = service.createDraft();

        assertThat(result.getId()).isEqualTo("cl_fixed");
        assertThat(result.getOwnerId()).isEqualTo("user_1");
        assertThat(result.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getUpdatedAt()).isEqualTo(now);
        assertThat(repository.findById("cl_fixed")).hasValueSatisfying(saved -> {
            assertThat(saved.getOwnerId()).isEqualTo("user_1");
            assertThat(saved.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
            assertThat(saved.getCreatedAt()).isEqualTo(now);
            assertThat(saved.getUpdatedAt()).isEqualTo(now);
        });
    }

    @Test
    void findMyCoverLettersReturnsCurrentUserPage() {
        Instant base = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.saveAll(List.of(
                draft("cl_old", "user_1", "Old title", base),
                draft("cl_new", "user_1", "New title", base.plusSeconds(60)),
                draft("cl_other", "user_2", "Other title", base.plusSeconds(120))
        ));

        Page<CoverLetter> page = service.findMyCoverLetters(1, 9, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(CoverLetter::getId)
                .containsExactly("cl_new", "cl_old");
    }

    @Test
    void findMyCoverLettersFiltersStatus() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(draft("cl_draft", "user_1", "Draft title", now));

        Page<CoverLetter> page = service.findMyCoverLetters(1, 9, CoverLetterStatus.DRAFT);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(CoverLetter::getId)
                .containsExactly("cl_draft");
    }

    @Test
    void findMyCoverLettersRejectsInvalidPageAndSize() {
        assertThatThrownBy(() -> service.findMyCoverLetters(0, 9, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> service.findMyCoverLetters(1, 10, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void deleteMyCoverLetterMarksCurrentUserOwnedCoverLetterDeleted() {
        Instant createdAt = Instant.parse("2026-06-20T01:00:00Z");
        Instant deletedAt = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(clock.instant()).willReturn(deletedAt);
        repository.save(draft("cl_delete", "user_1", "Delete title", createdAt));

        CoverLetter result = service.deleteMyCoverLetter("cl_delete");

        assertThat(result.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(result.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(repository.findById("cl_delete")).hasValueSatisfying(saved -> {
            assertThat(saved.getDeletedAt()).isEqualTo(deletedAt);
            assertThat(saved.getUpdatedAt()).isEqualTo(deletedAt);
        });
        assertThat(service.findMyCoverLetters(1, 9, null).getContent())
                .extracting(CoverLetter::getId)
                .doesNotContain("cl_delete");
    }

    @Test
    void deleteMyCoverLetterThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrAlreadyDeleted() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        CoverLetter deleted = draft("cl_deleted", "user_1", "Deleted title", now);
        deleted.markDeleted(now.plusSeconds(60));
        repository.saveAll(List.of(
                draft("cl_other", "user_2", "Other title", now),
                deleted
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.deleteMyCoverLetter("cl_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.deleteMyCoverLetter("cl_other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.deleteMyCoverLetter("cl_deleted"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void saveBasicInfoStoresTrimmedValuesAndUpdatesTimestamp() {
        Instant createdAt = Instant.parse("2026-06-20T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(clock.instant()).willReturn(updatedAt);
        repository.save(CoverLetter.draft("cl_basic", "user_1", createdAt));

        CoverLetter result = service.saveBasicInfo(
                "cl_basic",
                " 제목 ",
                " 회사 ",
                " 직무 ",
                " https://example.com/jobs/1 "
        );

        assertThat(result.getTitle()).isEqualTo("제목");
        assertThat(result.getCompanyName()).isEqualTo("회사");
        assertThat(result.getPositionTitle()).isEqualTo("직무");
        assertThat(result.getJobPostingUrl()).isEqualTo("https://example.com/jobs/1");
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(repository.findById("cl_basic")).hasValueSatisfying(saved -> {
            assertThat(saved.getTitle()).isEqualTo("제목");
            assertThat(saved.getCompanyName()).isEqualTo("회사");
            assertThat(saved.getPositionTitle()).isEqualTo("직무");
            assertThat(saved.getJobPostingUrl()).isEqualTo("https://example.com/jobs/1");
            assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt);
        });
    }

    @Test
    void saveBasicInfoStoresBlankJobPostingUrlAsNull() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(clock.instant()).willReturn(now.plusSeconds(60));
        repository.save(CoverLetter.draft("cl_basic", "user_1", now));

        CoverLetter result = service.saveBasicInfo(
                "cl_basic",
                "제목",
                "회사",
                "직무",
                " "
        );

        assertThat(result.getJobPostingUrl()).isNull();
    }

    @Test
    void saveBasicInfoThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrAlreadyDeleted() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        repository.saveAll(List.of(
                CoverLetter.draft("cl_other", "user_2", now),
                deleted
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.saveBasicInfo("cl_missing", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.saveBasicInfo("cl_other", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.saveBasicInfo("cl_deleted", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void saveBasicInfoThrowsCoverLetterNotDraftWhenStatusIsNotDraft() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.saveAll(List.of(
                coverLetterWithStatus("cl_reviewing", CoverLetterStatus.REVIEWING, now),
                coverLetterWithStatus("cl_reviewed", CoverLetterStatus.REVIEWED, now),
                coverLetterWithStatus("cl_failed", CoverLetterStatus.REVIEW_FAILED, now)
        ));

        assertThatThrownBy(() -> service.saveBasicInfo("cl_reviewing", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.saveBasicInfo("cl_reviewed", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.saveBasicInfo("cl_failed", "제목", "회사", "직무", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
    }

    @Test
    void saveBasicInfoThrowsValidationErrorWithDetails() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(CoverLetter.draft("cl_basic", "user_1", now));

        assertThatThrownBy(() -> service.saveBasicInfo(
                "cl_basic",
                " ",
                "회사",
                "직무",
                "not-a-url"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getDetails())
                            .extracting("field")
                            .containsExactly("title", "jobPostingUrl");
                });
    }

    @Test
    void savePreferencesStoresTrimmedValueAndUpdatesTimestamp() {
        Instant createdAt = Instant.parse("2026-06-20T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(clock.instant()).willReturn(updatedAt);
        repository.save(CoverLetter.draft("cl_preferences", "user_1", createdAt));

        CoverLetter result = service.savePreferences(
                "cl_preferences",
                " Spring Boot 경험, 대용량 트래픽 처리 경험 우대 "
        );

        assertThat(result.getPreferences()).isEqualTo("Spring Boot 경험, 대용량 트래픽 처리 경험 우대");
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(repository.findById("cl_preferences")).hasValueSatisfying(saved -> {
            assertThat(saved.getPreferences()).isEqualTo("Spring Boot 경험, 대용량 트래픽 처리 경험 우대");
            assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt);
        });
    }

    @Test
    void savePreferencesThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrAlreadyDeleted() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        repository.saveAll(List.of(
                CoverLetter.draft("cl_other", "user_2", now),
                deleted
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.savePreferences("cl_missing", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.savePreferences("cl_other", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.savePreferences("cl_deleted", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void savePreferencesThrowsCoverLetterNotDraftWhenStatusIsNotDraft() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.saveAll(List.of(
                coverLetterWithStatus("cl_reviewing", CoverLetterStatus.REVIEWING, now),
                coverLetterWithStatus("cl_reviewed", CoverLetterStatus.REVIEWED, now),
                coverLetterWithStatus("cl_failed", CoverLetterStatus.REVIEW_FAILED, now)
        ));

        assertThatThrownBy(() -> service.savePreferences("cl_reviewing", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.savePreferences("cl_reviewed", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.savePreferences("cl_failed", "Spring Boot 경험"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
    }

    @Test
    void savePreferencesThrowsValidationErrorWithDetails() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(CoverLetter.draft("cl_preferences", "user_1", now));

        assertThatThrownBy(() -> service.savePreferences(
                "cl_preferences",
                " "
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getDetails())
                            .extracting("field")
                            .containsExactly("preferences");
                });
    }

    @Test
    void savePreferencesRejectsTooLongPreferences() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(CoverLetter.draft("cl_preferences", "user_1", now));

        assertThatThrownBy(() -> service.savePreferences(
                "cl_preferences",
                "가".repeat(3001)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getDetails())
                            .extracting("field")
                            .containsExactly("preferences");
                });
    }

    @Test
    void saveQuestionsReplacesQuestionsWithServerAssignedOrderAndUpdatesTimestamp() {
        Instant createdAt = Instant.parse("2026-06-20T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("clq")).willReturn("clq_1", "clq_2");
        given(clock.instant()).willReturn(updatedAt);
        CoverLetter coverLetter = repository.save(CoverLetter.draft("cl_questions", "user_1", createdAt));
        questionRepository.save(CoverLetterQuestion.create(
                "clq_old",
                coverLetter,
                1,
                "기존 질문",
                1000,
                "기존 답변"
        ));

        SaveQuestionsResult result = service.saveQuestions(
                "cl_questions",
                List.of(
                        new SaveQuestionInput(" 지원 동기를 작성해주세요. ", 1000, " 제가 지원한 이유는... "),
                        new SaveQuestionInput(" 직무 관련 경험을 작성해주세요. ", 1500, " 저는 프로젝트에서... ")
                )
        );

        assertThat(result.coverLetter().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(result.questions()).extracting(CoverLetterQuestion::getId)
                .containsExactly("clq_1", "clq_2");
        assertThat(result.questions()).extracting(CoverLetterQuestion::getQuestionOrder)
                .containsExactly(1, 2);
        assertThat(result.questions()).extracting(CoverLetterQuestion::getQuestion)
                .containsExactly("지원 동기를 작성해주세요.", "직무 관련 경험을 작성해주세요.");
        assertThat(result.questions()).extracting(CoverLetterQuestion::getOriginalAnswer)
                .containsExactly("제가 지원한 이유는...", "저는 프로젝트에서...");
        assertThat(repository.findById("cl_questions")).hasValueSatisfying(saved ->
                assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt)
        );
        assertThat(questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc("cl_questions"))
                .extracting(CoverLetterQuestion::getId)
                .containsExactly("clq_1", "clq_2");
        assertThat(questionRepository.findById("clq_old")).isEmpty();
    }

    @Test
    void saveQuestionsThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrAlreadyDeleted() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        repository.saveAll(List.of(
                CoverLetter.draft("cl_other", "user_2", now),
                deleted
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        List<SaveQuestionInput> questions = List.of(
                new SaveQuestionInput("질문", 1000, "답변")
        );

        assertThatThrownBy(() -> service.saveQuestions("cl_missing", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.saveQuestions("cl_other", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.saveQuestions("cl_deleted", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void saveQuestionsThrowsCoverLetterNotDraftWhenStatusIsNotDraft() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.saveAll(List.of(
                coverLetterWithStatus("cl_reviewing", CoverLetterStatus.REVIEWING, now),
                coverLetterWithStatus("cl_reviewed", CoverLetterStatus.REVIEWED, now),
                coverLetterWithStatus("cl_failed", CoverLetterStatus.REVIEW_FAILED, now)
        ));
        List<SaveQuestionInput> questions = List.of(
                new SaveQuestionInput("질문", 1000, "답변")
        );

        assertThatThrownBy(() -> service.saveQuestions("cl_reviewing", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.saveQuestions("cl_reviewed", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
        assertThatThrownBy(() -> service.saveQuestions("cl_failed", questions))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_DRAFT);
    }

    @Test
    void saveQuestionsThrowsValidationErrorWithDetails() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(CoverLetter.draft("cl_questions", "user_1", now));

        assertThatThrownBy(() -> service.saveQuestions(
                "cl_questions",
                List.of(new SaveQuestionInput(" ", 99, " "))
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getDetails())
                            .extracting("field")
                            .containsExactly(
                                    "questions[0].question",
                                    "questions[0].maxAnswerLength",
                                    "questions[0].originalAnswer"
                            );
                });
    }

    @Test
    void saveQuestionsRequiresAtLeastOneQuestion() {
        Instant now = Instant.parse("2026-06-20T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        repository.save(CoverLetter.draft("cl_questions", "user_1", now));

        assertThatThrownBy(() -> service.saveQuestions("cl_questions", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getDetails())
                            .extracting("field")
                            .containsExactly("questions");
                });
    }

    private CoverLetter draft(String id, String ownerId, String title, Instant now) {
        CoverLetter coverLetter = CoverLetter.draft(id, ownerId, now);
        coverLetter.fillBasicInfo(title, "Rewrite Corp", "백엔드 개발자", null, now);
        return coverLetter;
    }

    private CoverLetter coverLetterWithStatus(String id, CoverLetterStatus status, Instant now) {
        CoverLetter coverLetter = CoverLetter.draft(id, "user_1", now);
        ReflectionTestUtils.setField(coverLetter, "status", status);
        return coverLetter;
    }
}
