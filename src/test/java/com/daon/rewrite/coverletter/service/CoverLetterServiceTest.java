package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
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

    private CoverLetter draft(String id, String ownerId, String title, Instant now) {
        CoverLetter coverLetter = CoverLetter.draft(id, ownerId, now);
        coverLetter.fillBasicInfo(title, "Rewrite Corp", "백엔드 개발자", null, now);
        return coverLetter;
    }
}
