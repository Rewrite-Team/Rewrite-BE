package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisKeywordRepository;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class KeywordAnalysisServiceTest {

    @Autowired
    private KeywordAnalysisService service;

    @Autowired
    private KeywordAnalysisRepository keywordAnalysisRepository;

    @Autowired
    private KeywordAnalysisKeywordRepository keywordAnalysisKeywordRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        keywordAnalysisKeywordRepository.deleteAll();
        keywordAnalysisRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        llmJobRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void startMyKeywordAnalysisCreatesProcessingAnalysisAndPendingJobWithLatestVersion() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("ka")).willReturn("ka_1");
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartKeywordAnalysisResult result = service.startMyKeywordAnalysis("cl_1", null);

        assertThat(result.keywordAnalysis().getId()).isEqualTo("ka_1");
        assertThat(result.keywordAnalysis().getSourceReviewVersionId()).isEqualTo("rv_1");
        assertThat(result.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.PROCESSING);
        assertThat(result.job().getId()).isEqualTo("job_1");
        assertThat(result.job().getType()).isEqualTo(LlmJobType.KEYWORD_ANALYSIS);
        assertThat(result.job().getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(result.job().getTargetId()).isEqualTo("cl_1");
    }

    @Test
    void startMyKeywordAnalysisUsesRequestedSourceReviewVersionWhenItBelongsToCoverLetter() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_2", now);
        reviewVersionRepository.save(ReviewVersion.reReview(
                "rv_1",
                coverLetter,
                "v0.0",
                null,
                now.plusSeconds(30)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("ka")).willReturn("ka_1");
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartKeywordAnalysisResult result = service.startMyKeywordAnalysis("cl_1", "rv_1");

        assertThat(result.keywordAnalysis().getSourceReviewVersionId()).isEqualTo("rv_1");
    }

    @Test
    void startMyKeywordAnalysisReusesExistingAnalysisForReanalysis() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_2", now);
        KeywordAnalysis existing = KeywordAnalysis.processing("ka_existing", coverLetter, "rv_1", now.plusSeconds(30));
        keywordAnalysisRepository.save(existing);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartKeywordAnalysisResult result = service.startMyKeywordAnalysis("cl_1", null);

        assertThat(result.keywordAnalysis().getId()).isEqualTo("ka_existing");
        assertThat(result.keywordAnalysis().getSourceReviewVersionId()).isEqualTo("rv_2");
        assertThat(result.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.PROCESSING);
        assertThat(keywordAnalysisRepository.count()).isEqualTo(1);
    }

    @Test
    void startMyKeywordAnalysisRejectsMissingOtherOwnerDeletedOrNotReviewedCoverLetter() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_other", "user_2", "rv_other", now);
        CoverLetter deleted = saveReviewedCoverLetter("cl_deleted", "user_1", "rv_deleted", now);
        deleted.markDeleted(now.plusSeconds(120));
        coverLetterRepository.save(deleted);
        coverLetterRepository.save(CoverLetter.draft("cl_draft", "user_1", now));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound("cl_missing");
        assertNotFound("cl_other");
        assertNotFound("cl_deleted");
        assertThatThrownBy(() -> service.startMyKeywordAnalysis("cl_draft", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void startMyKeywordAnalysisRejectsSourceReviewVersionFromOtherCoverLetter() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        saveReviewedCoverLetter("cl_2", "user_1", "rv_other", now);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.startMyKeywordAnalysis("cl_1", "rv_other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void startMyKeywordAnalysisRejectsWhenJobAlreadyRunning() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        llmJobRepository.save(LlmJob.pendingReview("job_running", "cl_1", now.plusSeconds(60), 1));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.startMyKeywordAnalysis("cl_1", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_JOB_ALREADY_RUNNING);
    }

    @Test
    void findMyLatestKeywordAnalysisReturnsEmptyWhenAnalysisDoesNotExist() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        LatestKeywordAnalysisResult result = service.findMyLatestKeywordAnalysis("cl_1");

        assertThat(result.coverLetterId()).isEqualTo("cl_1");
        assertThat(result.keywordAnalysis()).isNull();
        assertThat(result.keywords()).isEmpty();
    }

    @Test
    void findMyLatestKeywordAnalysisReturnsCompletedAnalysisWithOrderedKeywords() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.save(KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        ));
        keywordAnalysis.complete(now.plusSeconds(180));
        keywordAnalysisRepository.save(keywordAnalysis);
        keywordAnalysisKeywordRepository.save(KeywordAnalysisKeyword.of("kak_2", keywordAnalysis, 2, "Spring", 88));
        keywordAnalysisKeywordRepository.save(KeywordAnalysisKeyword.of("kak_1", keywordAnalysis, 1, "백엔드", 95));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        LatestKeywordAnalysisResult result = service.findMyLatestKeywordAnalysis("cl_1");

        assertThat(result.keywordAnalysis().getId()).isEqualTo("ka_1");
        assertThat(result.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.COMPLETED);
        assertThat(result.keywords())
                .extracting(KeywordAnalysisKeyword::getKeyword, KeywordAnalysisKeyword::getImportance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("백엔드", 95),
                        org.assertj.core.groups.Tuple.tuple("Spring", 88)
                );
    }

    @Test
    void findMyLatestKeywordAnalysisReturnsFailedAnalysisWithError() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        KeywordAnalysis keywordAnalysis = KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        );
        keywordAnalysis.fail(now.plusSeconds(180));
        keywordAnalysisRepository.save(keywordAnalysis);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        LatestKeywordAnalysisResult result = service.findMyLatestKeywordAnalysis("cl_1");

        assertThat(result.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
        assertThat(result.keywords()).isEmpty();
    }

    @Test
    void findMyLatestKeywordAnalysisHidesKeywordsWhenAnalysisIsNotCompleted() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.save(KeywordAnalysis.processing(
                "ka_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        ));
        keywordAnalysisKeywordRepository.save(KeywordAnalysisKeyword.of("kak_1", keywordAnalysis, 1, "백엔드", 95));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        LatestKeywordAnalysisResult processingResult = service.findMyLatestKeywordAnalysis("cl_1");

        assertThat(processingResult.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.PROCESSING);
        assertThat(processingResult.keywords()).isEmpty();

        keywordAnalysis.fail(now.plusSeconds(180));
        keywordAnalysisRepository.save(keywordAnalysis);

        LatestKeywordAnalysisResult failedResult = service.findMyLatestKeywordAnalysis("cl_1");

        assertThat(failedResult.keywordAnalysis().getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
        assertThat(failedResult.keywords()).isEmpty();
    }

    @Test
    void findMyLatestKeywordAnalysisRejectsMissingOtherOwnerOrDeletedCoverLetter() {
        Instant now = Instant.parse("2026-07-03T01:00:00Z");
        saveReviewedCoverLetter("cl_other", "user_2", "rv_other", now);
        CoverLetter deleted = saveReviewedCoverLetter("cl_deleted", "user_1", "rv_deleted", now);
        deleted.markDeleted(now.plusSeconds(120));
        coverLetterRepository.save(deleted);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertLatestNotFound("cl_missing");
        assertLatestNotFound("cl_other");
        assertLatestNotFound("cl_deleted");
    }

    private void assertNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.startMyKeywordAnalysis(coverLetterId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void assertLatestNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.findMyLatestKeywordAnalysis(coverLetterId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private CoverLetter saveReviewedCoverLetter(String coverLetterId, String ownerId, String versionId, Instant now) {
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, ownerId, now);
        coverLetterRepository.save(coverLetter);
        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                versionId,
                coverLetter,
                now.plusSeconds(60)
        ));
        coverLetter.completeReview(reviewVersion.getId(), now.plusSeconds(90));
        return coverLetterRepository.saveAndFlush(coverLetter);
    }
}
