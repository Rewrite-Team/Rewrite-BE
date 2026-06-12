package com.daon.rewrite.coverletter;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.dto.CoverLetterCreateRequest;
import com.daon.rewrite.coverletter.dto.CoverLetterCreateResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDeleteResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("CoverLetter 기본 CRUD 구현 PR에서 활성화")
class CoverLetterServiceTest {

    private final CoverLetterRepository repository = new CoverLetterRepository();
    private final CoverLetterService service = new CoverLetterService(
            repository,
            currentUserProvider("user_dev_001")
    );

    @Test
    void createCoverLetterStoresCurrentUserIdAndDraftStatus() {
        CoverLetterCreateResponse response = service.create(new CoverLetterCreateRequest(
                "백엔드 자기소개서",
                "Rewrite",
                "백엔드 개발자",
                "https://example.com/jobs/1"
        ));

        CoverLetter saved = repository.findById(response.id()).orElseThrow();
        assertEquals("user_dev_001", saved.userId());
        assertEquals(CoverLetterStatus.DRAFT, saved.status());
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());
        assertNull(saved.deletedAt());
    }

    @Test
    void createCoverLetterTrimsValuesAndStoresBlankJobPostingUrlAsNull() {
        CoverLetterCreateResponse response = service.create(new CoverLetterCreateRequest(
                "  백엔드 자기소개서  ",
                "  Rewrite  ",
                "  백엔드 개발자  ",
                "   "
        ));

        CoverLetter saved = repository.findById(response.id()).orElseThrow();
        assertEquals("백엔드 자기소개서", saved.title());
        assertEquals("Rewrite", saved.companyName());
        assertEquals("백엔드 개발자", saved.positionTitle());
        assertNull(saved.jobPostingUrl());
    }

    @Test
    void findAllReturnsOnlyCurrentUsersActiveCoverLetters() {
        CoverLetterCreateResponse currentUserCoverLetter = service.create(new CoverLetterCreateRequest(
                "현재 사용자 자기소개서",
                "Rewrite",
                "백엔드 개발자",
                null
        ));
        CoverLetterCreateResponse deletedCoverLetter = service.create(new CoverLetterCreateRequest(
                "삭제될 자기소개서",
                "Rewrite",
                "백엔드 개발자",
                null
        ));

        CoverLetterService otherUserService = new CoverLetterService(
                repository,
                currentUserProvider("user_other_001")
        );
        otherUserService.create(new CoverLetterCreateRequest(
                "다른 사용자 자기소개서",
                "Other",
                "프론트엔드 개발자",
                null
        ));
        service.delete(deletedCoverLetter.id());

        CoverLetterListResponse response = service.findAll();

        assertEquals(1, response.items().size());
        assertEquals(currentUserCoverLetter.id(), response.items().get(0).id());
        assertEquals("현재 사용자 자기소개서", response.items().get(0).title());
        assertEquals(1, response.totalItems());
        assertEquals(1, response.totalPages());
    }

    @Test
    void findByIdReturnsCurrentUsersActiveCoverLetter() {
        CoverLetterCreateResponse created = service.create(new CoverLetterCreateRequest(
                "백엔드 자기소개서",
                "Rewrite",
                "백엔드 개발자",
                "https://example.com/jobs/1"
        ));

        CoverLetterDetailResponse response = service.findById(created.id());

        assertEquals(created.id(), response.id());
        assertEquals("백엔드 자기소개서", response.title());
        assertEquals("Rewrite", response.companyName());
        assertEquals("백엔드 개발자", response.positionTitle());
        assertEquals("https://example.com/jobs/1", response.jobPostingUrl());
        assertEquals(CoverLetterStatus.DRAFT, response.status());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    void findByIdThrowsNotFoundWhenCoverLetterDoesNotExist() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.findById("missing")
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void findByIdThrowsNotFoundWhenOwnerDoesNotMatch() {
        CoverLetterService otherUserService = new CoverLetterService(
                repository,
                currentUserProvider("user_other_001")
        );
        CoverLetterCreateResponse otherUserCoverLetter = otherUserService.create(new CoverLetterCreateRequest(
                "다른 사용자 자기소개서",
                "Other",
                "프론트엔드 개발자",
                null
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.findById(otherUserCoverLetter.id())
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteCoverLetterRecordsDeletedAtAndHidesCoverLetter() {
        CoverLetterCreateResponse created = service.create(new CoverLetterCreateRequest(
                "백엔드 자기소개서",
                "Rewrite",
                "백엔드 개발자",
                null
        ));

        CoverLetterDeleteResponse response = service.delete(created.id());

        CoverLetter deleted = repository.findById(created.id()).orElseThrow();
        assertEquals(true, response.success());
        assertNotNull(response.deletedAt());
        assertNotNull(deleted.deletedAt());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.findById(created.id())
        );
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteCoverLetterThrowsNotFoundWhenOwnerDoesNotMatch() {
        CoverLetterService otherUserService = new CoverLetterService(
                repository,
                currentUserProvider("user_other_001")
        );
        CoverLetterCreateResponse otherUserCoverLetter = otherUserService.create(new CoverLetterCreateRequest(
                "다른 사용자 자기소개서",
                "Other",
                "프론트엔드 개발자",
                null
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.delete(otherUserCoverLetter.id())
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        CoverLetter stillActive = repository.findById(otherUserCoverLetter.id()).orElseThrow();
        assertNull(stillActive.deletedAt());
    }

    private CurrentUserProvider currentUserProvider(String userId) {
        return () -> new CurrentUser(userId, "테스트 사용자", "https://example.com/profile.png");
    }
}
