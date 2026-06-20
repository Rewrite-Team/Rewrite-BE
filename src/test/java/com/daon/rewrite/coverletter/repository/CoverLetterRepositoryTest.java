package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    void findActiveByIdAndOwnerReturnsOnlyCurrentOwnerUndeletedCoverLetter() {
        CoverLetter active = draft("cl_active", "user_1", "Active title", "2026-06-20T01:00:00Z");
        CoverLetter deleted = draft("cl_deleted", "user_1", "Deleted title", "2026-06-20T02:00:00Z");
        deleted.markDeleted(Instant.parse("2026-06-20T03:00:00Z"));
        CoverLetter otherOwner = draft("cl_other", "user_2", "Other title", "2026-06-20T04:00:00Z");
        repository.saveAll(List.of(active, deleted, otherOwner));
        entityManager.flush();
        entityManager.clear();

        Optional<CoverLetter> found = repository.findByIdAndOwnerIdAndDeletedAtIsNull("cl_active", "user_1");

        assertThat(found).hasValueSatisfying(coverLetter ->
                assertThat(coverLetter.getId()).isEqualTo("cl_active")
        );
        assertThat(repository.findByIdAndOwnerIdAndDeletedAtIsNull("cl_deleted", "user_1")).isEmpty();
        assertThat(repository.findByIdAndOwnerIdAndDeletedAtIsNull("cl_other", "user_1")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findActiveForUpdateBlocksConcurrentTransaction() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> repository.save(
                CoverLetter.draft("cl_locked", "user_1", Instant.parse("2026-06-20T01:00:00Z"))
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                repository.findActiveByIdAndOwnerIdForUpdate("cl_locked", "user_1").orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status ->
                    repository.findActiveByIdAndOwnerIdForUpdate("cl_locked", "user_1").orElseThrow()
            ));

            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> repository.deleteAll());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private CoverLetter draft(String id, String ownerId, String title, String createdAt) {
        CoverLetter coverLetter = CoverLetter.draft(id, ownerId, Instant.parse(createdAt));
        coverLetter.fillBasicInfo(title, "Rewrite Corp", "백엔드 개발자", null, Instant.parse(createdAt));
        return coverLetter;
    }
}
