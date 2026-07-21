package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewVersionRepositoryTest {

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository coverLetterQuestionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveFirstReviewVersionRoundTripsQuestionSnapshots() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        CoverLetterQuestion question = coverLetterQuestionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "지원 동기를 작성해주세요.",
                1000,
                "원본 답변😀"
        ));
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_1",
                coverLetter,
                now.plusSeconds(60)
        ));
        questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                question,
                "STAR 기준으로 성과를 구체화해야 합니다.",
                "수정 답변😀"
        ));

        entityManager.flush();
        entityManager.clear();

        ReviewVersion foundVersion = reviewVersionRepository.findById("rv_1").orElseThrow();
        List<ReviewVersionQuestionResult> foundResults =
                questionResultRepository.findByReviewVersionIdOrderByQuestionOrderAsc("rv_1");

        assertThat(foundVersion.getCoverLetter().getId()).isEqualTo("cl_1");
        assertThat(foundVersion.getVersion()).isEqualTo("v0.1");
        assertThat(foundVersion.getRequestInstruction()).isNull();
        assertThat(foundVersion.getCreatedAt()).isEqualTo(now.plusSeconds(60));

        assertThat(foundResults).hasSize(1);
        assertThat(foundResults.getFirst().getId()).isEqualTo("rvqr_1");
        assertThat(foundResults.getFirst().getReviewVersion().getId()).isEqualTo("rv_1");
        assertThat(foundResults.getFirst().getQuestion().getId()).isEqualTo("clq_1");
        assertThat(foundResults.getFirst().getQuestionOrder()).isEqualTo(1);
        assertThat(foundResults.getFirst().getQuestionText()).isEqualTo("지원 동기를 작성해주세요.");
        assertThat(foundResults.getFirst().getMaxAnswerLength()).isEqualTo(1000);
        assertThat(foundResults.getFirst().getOriginalAnswer()).isEqualTo("원본 답변😀");
        assertThat(foundResults.getFirst().getOriginalAnswerLength()).isEqualTo(6);
        assertThat(foundResults.getFirst().getAiReport()).isEqualTo("STAR 기준으로 성과를 구체화해야 합니다.");
        assertThat(foundResults.getFirst().getRewrittenAnswer()).isEqualTo("수정 답변😀");
        assertThat(foundResults.getFirst().getRewrittenAnswerLength()).isEqualTo(6);
        assertThat(foundResults.getFirst().getFinalAnswer()).isEqualTo("수정 답변😀");
        assertThat(foundResults.getFirst().getFinalAnswerLength()).isEqualTo(6);
    }

    @Test
    void findByCoverLetterIdOrdersByCreatedAtAsc() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        ReviewVersion oldVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_old",
                coverLetter,
                now.plusSeconds(60)
        ));
        ReviewVersion newVersion = ReviewVersion.first(
                "rv_new",
                coverLetter,
                now.plusSeconds(120)
        );
        ReflectionTestUtils.setField(newVersion, "version", "v0.2");
        reviewVersionRepository.save(newVersion);

        List<ReviewVersion> result = reviewVersionRepository.findByCoverLetterIdOrderByCreatedAtAsc("cl_1");

        assertThat(result).extracting(ReviewVersion::getId)
                .containsExactly("rv_old", "rv_new");
    }

    @Test
    void findByIdAndCoverLetterIdReturnsOnlyMatchingCoverLetterVersion() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter myCoverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        CoverLetter otherCoverLetter = coverLetterRepository.save(CoverLetter.draft("cl_2", "user_1", now));
        reviewVersionRepository.save(ReviewVersion.first("rv_1", myCoverLetter, now.plusSeconds(60)));
        reviewVersionRepository.save(ReviewVersion.first("rv_other", otherCoverLetter, now.plusSeconds(60)));

        assertThat(reviewVersionRepository.findByIdAndCoverLetterId("rv_1", "cl_1"))
                .hasValueSatisfying(version -> assertThat(version.getId()).isEqualTo("rv_1"));
        assertThat(reviewVersionRepository.findByIdAndCoverLetterId("rv_other", "cl_1"))
                .isEmpty();
    }

    @Test
    void updateFinalAnswerRoundTripsUpdatedAnswerAndLength() {
        Instant now = Instant.parse("2026-06-21T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        CoverLetterQuestion question = coverLetterQuestionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "지원 동기를 작성해주세요.",
                1000,
                "원본 답변"
        ));
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                "rv_1",
                coverLetter,
                now.plusSeconds(60)
        ));
        ReviewVersionQuestionResult questionResult = questionResultRepository.save(ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                question,
                "AI 리포트",
                "수정 답변"
        ));

        questionResult.updateFinalAnswer("최종 답변😀");
        entityManager.flush();
        entityManager.clear();

        ReviewVersionQuestionResult found = questionResultRepository.findById("rvqr_1").orElseThrow();

        assertThat(found.getFinalAnswer()).isEqualTo("최종 답변😀");
        assertThat(found.getFinalAnswerLength()).isEqualTo(6);
    }
}
