package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@ActiveProfiles("test")
class CoverLetterRepositoryTest {

    @Autowired
    private CoverLetterRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindDraftRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        CoverLetter draft = CoverLetter.draft("cl_fixed", "user_1", now);

        repository.save(draft);
        entityManager.flush();
        entityManager.clear();

        CoverLetter found = repository.findById("cl_fixed").orElseThrow();

        assertThat(found.getId()).isEqualTo("cl_fixed");
        assertThat(found.getOwnerId()).isEqualTo("user_1");
        assertThat(found.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(found.getCreatedAt()).isEqualTo(now);
        assertThat(found.getUpdatedAt()).isEqualTo(now);
        assertThat(found.getTitle()).isNull();
        assertThat(found.getCompanyName()).isNull();
        assertThat(found.getPositionTitle()).isNull();
        assertThat(found.getJobPostingUrl()).isNull();
        assertThat(found.getPreferences()).isNull();
        assertThat(found.getSubmittedAt()).isNull();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getLatestReviewVersionId()).isNull();
    }

    @Test
    void findActiveByOwnerOrdersByCreatedAtDesc() {
        CoverLetter oldOne = draft("cl_old", "user_1", "Old title", "2026-06-20T01:00:00Z");
        CoverLetter newOne = draft("cl_new", "user_1", "New title", "2026-06-20T03:00:00Z");
        CoverLetter otherOwner = draft("cl_other", "user_2", "Other title", "2026-06-20T04:00:00Z");
        CoverLetter deleted = draft("cl_deleted", "user_1", "Deleted title", "2026-06-20T05:00:00Z");
        deleted.markDeleted(Instant.parse("2026-06-20T06:00:00Z"));
        repository.saveAll(List.of(oldOne, newOne, otherOwner, deleted));
        entityManager.flush();
        entityManager.clear();

        Page<CoverLetter> page = repository.findByOwnerIdAndDeletedAtIsNull(
                "user_1",
                PageRequest.of(0, 9, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(CoverLetter::getId)
                .containsExactly("cl_new", "cl_old");
    }

    @Test
    void findActiveByOwnerAndStatusFiltersStatus() {
        repository.saveAll(List.of(
                draft("cl_draft", "user_1", "Draft title", "2026-06-20T01:00:00Z"),
                draft("cl_other_owner", "user_2", "Other title", "2026-06-20T02:00:00Z")
        ));
        entityManager.flush();
        entityManager.clear();

        Page<CoverLetter> page = repository.findByOwnerIdAndStatusAndDeletedAtIsNull(
                "user_1",
                CoverLetterStatus.DRAFT,
                PageRequest.of(0, 9, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(CoverLetter::getId)
                .containsExactly("cl_draft");
    }

    @Test
    void findActiveByOwnerPaginates() {
        repository.saveAll(List.of(
                draft("cl_1", "user_1", "Title 1", "2026-06-20T01:00:00Z"),
                draft("cl_2", "user_1", "Title 2", "2026-06-20T02:00:00Z"),
                draft("cl_3", "user_1", "Title 3", "2026-06-20T03:00:00Z")
        ));
        entityManager.flush();
        entityManager.clear();

        Page<CoverLetter> page = repository.findByOwnerIdAndDeletedAtIsNull(
                "user_1",
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(CoverLetter::getId)
                .containsExactly("cl_1");
    }

    private CoverLetter draft(String id, String ownerId, String title, String createdAt) {
        CoverLetter coverLetter = CoverLetter.draft(id, ownerId, Instant.parse(createdAt));
        coverLetter.fillBasicInfo(title, "Rewrite Corp", "백엔드 개발자", null, Instant.parse(createdAt));
        return coverLetter;
    }
}
