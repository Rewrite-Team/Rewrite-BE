package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class ReviewVersionQueryServiceTest {

    @Autowired
    private ReviewVersionQueryService service;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @AfterEach
    void cleanUp() {
        questionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void findMyReviewVersionsReturnsCurrentUserVersionsWithLatestFlag() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        ReviewVersion oldVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_old",
                coverLetter,
                now.plusSeconds(60)
        ));
        ReviewVersion newVersion = ReviewVersion.first("rv_new", coverLetter, now.plusSeconds(120));
        ReflectionTestUtils.setField(newVersion, "version", "v0.2");
        reviewVersionRepository.save(newVersion);
        coverLetter.setLatestReviewVersionId("rv_new");
        coverLetterRepository.saveAndFlush(coverLetter);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        List<ReviewVersionSummary> result = service.findMyReviewVersions("cl_1");

        assertThat(result).extracting(summary -> summary.reviewVersion().getId())
                .containsExactly("rv_new", oldVersion.getId());
        assertThat(result).extracting(ReviewVersionSummary::isLatest)
                .containsExactly(true, false);
    }

    @Test
    void findMyReviewVersionReturnsDetailWithQuestionResultsInOrder() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_1",
                coverLetter,
                now.plusSeconds(60)
        ));
        coverLetter.setLatestReviewVersionId("rv_1");
        coverLetterRepository.saveAndFlush(coverLetter);
        CoverLetterQuestion firstQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "지원 동기는?",
                1000,
                "원본 답변 1"
        ));
        CoverLetterQuestion secondQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_2",
                coverLetter,
                2,
                "직무 역량은?",
                1500,
                "원본 답변 2"
        ));
        questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_2",
                reviewVersion,
                secondQuestion,
                "두 번째 리포트",
                "두 번째 수정본"
        ));
        questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                firstQuestion,
                "첫 번째 리포트",
                "첫 번째 수정본"
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        ReviewVersionDetail result = service.findMyReviewVersion("cl_1", "rv_1");

        assertThat(result.reviewVersion().getId()).isEqualTo("rv_1");
        assertThat(result.isLatest()).isTrue();
        assertThat(result.questionResults()).extracting(ReviewVersionQuestionResult::getId)
                .containsExactly("rvqr_1", "rvqr_2");
    }

    @Test
    void findMyReviewVersionsThrowsNotFoundWhenCoverLetterIsMissingOtherOwnerOrDeleted() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        coverLetterRepository.save(deleted);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound(() -> service.findMyReviewVersions("cl_missing"));
        assertNotFound(() -> service.findMyReviewVersions("cl_other"));
        assertNotFound(() -> service.findMyReviewVersions("cl_deleted"));
    }

    @Test
    void findMyReviewVersionThrowsNotFoundWhenVersionDoesNotBelongToCoverLetter() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        CoverLetter otherCoverLetter = coverLetterRepository.save(CoverLetter.draft("cl_2", "user_1", now));
        reviewVersionRepository.save(ReviewVersion.first("rv_1", coverLetter, now.plusSeconds(60)));
        reviewVersionRepository.save(ReviewVersion.first("rv_other", otherCoverLetter, now.plusSeconds(60)));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound(() -> service.findMyReviewVersion("cl_1", "rv_missing"));
        assertNotFound(() -> service.findMyReviewVersion("cl_1", "rv_other"));
    }

    private void assertNotFound(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void invoke();
    }
}
