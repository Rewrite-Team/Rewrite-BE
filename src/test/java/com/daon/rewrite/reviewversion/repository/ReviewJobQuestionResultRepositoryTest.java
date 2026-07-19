package com.daon.rewrite.reviewversion.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewJobQuestionResultRepositoryTest {

    @Autowired
    private ReviewJobQuestionResultRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesInputSnapshotAndCompletedResult() {
        Instant now = Instant.parse("2026-07-17T01:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft("cl_1", "user_1", now);
        entityManager.persist(coverLetter);
        CoverLetterQuestion question = CoverLetterQuestion.create(
                "clq_1", coverLetter, 1, "지원 동기는?", 1000, "원본 답변😀"
        );
        entityManager.persist(question);
        LlmJob job = LlmJob.pendingReview("job_1", coverLetter.getId(), now, 1);
        entityManager.persist(job);
        ReviewJobQuestionResult result = repository.save(ReviewJobQuestionResult.processing(
                "rjqr_1", job, question, "원본 답변😀"
        ));

        result.complete("AI 리포트", "AI 수정본😀", now.plusSeconds(30));
        repository.flush();
        entityManager.clear();

        assertThat(repository.findByLlmJobIdOrderByQuestionOrderAsc("job_1"))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.getQuestion().getId()).isEqualTo("clq_1");
                    assertThat(found.getInputAnswer()).isEqualTo("원본 답변😀");
                    assertThat(found.getInputAnswerLength()).isEqualTo(6);
                    assertThat(found.getStatus()).isEqualTo(ReviewJobQuestionResultStatus.COMPLETED);
                    assertThat(found.getAiReport()).isEqualTo("AI 리포트");
                    assertThat(found.getRewrittenAnswer()).isEqualTo("AI 수정본😀");
                    assertThat(found.getFinalAnswer()).isEqualTo("AI 수정본😀");
                    assertThat(found.getCompletedAt()).isEqualTo(now.plusSeconds(30));
                });
    }
}
