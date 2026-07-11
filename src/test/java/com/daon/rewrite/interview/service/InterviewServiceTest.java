package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class InterviewServiceTest {

    @Autowired
    private InterviewService service;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewThreadRepository interviewThreadRepository;

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
        interviewThreadRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        llmJobRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void startMyInterviewCreatesQuestionGeneratingSessionAndPendingJobWithLatestVersion() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("is")).willReturn("is_1");
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartInterviewResult result = service.startMyInterview("cl_1", null);

        assertThat(result.interviewSession().getId()).isEqualTo("is_1");
        assertThat(result.interviewSession().getInitialSourceReviewVersionId()).isEqualTo("rv_1");
        assertThat(result.interviewSession().getStatus()).isEqualTo(InterviewSessionStatus.QUESTION_GENERATING);
        assertThat(result.job().getId()).isEqualTo("job_1");
        assertThat(result.job().getType()).isEqualTo(LlmJobType.INTERVIEW_QUESTION_GENERATION);
        assertThat(result.job().getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(result.job().getTargetId()).isEqualTo("cl_1");
        assertThat(result.job().getProgressTotal()).isEqualTo(5);
    }

    @Test
    void startMyInterviewUsesRequestedSourceReviewVersionWhenItBelongsToCoverLetter() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_2", now);
        reviewVersionRepository.save(ReviewVersion.reReview(
                "rv_1",
                coverLetter,
                "v0.0",
                null,
                now.plusSeconds(30)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("is")).willReturn("is_1");
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartInterviewResult result = service.startMyInterview("cl_1", " rv_1 ");

        assertThat(result.interviewSession().getInitialSourceReviewVersionId()).isEqualTo("rv_1");
    }

    @Test
    void startMyInterviewReturnsExistingActiveOrQuestionGeneratingSessionWithoutNewJob() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter activeCoverLetter = saveReviewedCoverLetter("cl_active", "user_1", "rv_active", now);
        InterviewSession activeSession = InterviewSession.questionGenerating(
                "is_active",
                activeCoverLetter,
                "rv_active",
                now.plusSeconds(120)
        );
        activeSession.activate();
        interviewSessionRepository.save(activeSession);

        CoverLetter generatingCoverLetter = saveReviewedCoverLetter(
                "cl_generating",
                "user_1",
                "rv_generating",
                now.plusSeconds(180)
        );
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_generating",
                generatingCoverLetter,
                "rv_generating",
                now.plusSeconds(300)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        StartInterviewResult activeResult = service.startMyInterview("cl_active", null);
        StartInterviewResult generatingResult = service.startMyInterview("cl_generating", null);

        assertThat(activeResult.interviewSession().getId()).isEqualTo("is_active");
        assertThat(activeResult.interviewSession().getStatus()).isEqualTo(InterviewSessionStatus.ACTIVE);
        assertThat(activeResult.job()).isNull();
        assertThat(generatingResult.interviewSession().getId()).isEqualTo("is_generating");
        assertThat(generatingResult.interviewSession().getStatus())
                .isEqualTo(InterviewSessionStatus.QUESTION_GENERATING);
        assertThat(generatingResult.job()).isNull();
        assertThat(interviewSessionRepository.count()).isEqualTo(2);
        assertThat(llmJobRepository.count()).isZero();
    }

    @Test
    void startMyInterviewRestartsFailedSessionWithSameIdAndLatestVersion() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_2", now);
        InterviewSession failedSession = InterviewSession.questionGenerating(
                "is_existing",
                coverLetter,
                "rv_1",
                now.plusSeconds(30)
        );
        failedSession.fail();
        interviewSessionRepository.save(failedSession);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(120));

        StartInterviewResult result = service.startMyInterview("cl_1", null);

        assertThat(result.interviewSession().getId()).isEqualTo("is_existing");
        assertThat(result.interviewSession().getInitialSourceReviewVersionId()).isEqualTo("rv_2");
        assertThat(result.interviewSession().getStatus()).isEqualTo(InterviewSessionStatus.QUESTION_GENERATING);
        assertThat(result.job().getId()).isEqualTo("job_1");
        assertThat(interviewSessionRepository.count()).isEqualTo(1);
    }

    @Test
    void startMyInterviewRejectsMissingOtherOwnerDeletedOrNotReviewedCoverLetter() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        saveReviewedCoverLetter("cl_other", "user_2", "rv_other", now);
        CoverLetter deleted = saveReviewedCoverLetter("cl_deleted", "user_1", "rv_deleted", now);
        deleted.markDeleted(now.plusSeconds(120));
        coverLetterRepository.save(deleted);
        coverLetterRepository.save(CoverLetter.draft("cl_draft", "user_1", now));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound("cl_missing");
        assertNotFound("cl_other");
        assertNotFound("cl_deleted");
        assertThatThrownBy(() -> service.startMyInterview("cl_draft", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void startMyInterviewRejectsSourceReviewVersionFromOtherCoverLetter() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        saveReviewedCoverLetter("cl_2", "user_1", "rv_other", now.plusSeconds(180));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.startMyInterview("cl_1", "rv_other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void startMyInterviewValidatesRequestedSourceVersionEvenWhenActiveSessionExists() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        saveReviewedCoverLetter("cl_2", "user_1", "rv_other", now.plusSeconds(180));
        InterviewSession activeSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        );
        activeSession.activate();
        interviewSessionRepository.save(activeSession);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.startMyInterview("cl_1", "rv_other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(interviewSessionRepository.findById("is_1").orElseThrow().getStatus())
                .isEqualTo(InterviewSessionStatus.ACTIVE);
        assertThat(llmJobRepository.count()).isZero();
    }

    @Test
    void startMyInterviewRejectsWhenJobAlreadyRunning() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        llmJobRepository.save(LlmJob.pendingReview("job_running", "cl_1", now.plusSeconds(60), 1));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.startMyInterview("cl_1", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        assertThat(interviewSessionRepository.count()).isZero();
    }

    @Test
    void findMyCurrentInterviewReturnsNullWhenSessionDoesNotExist() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        CurrentInterviewResult result = service.findMyCurrentInterview("cl_1");

        assertThat(result.coverLetterId()).isEqualTo("cl_1");
        assertThat(result.interviewSession()).isNull();
    }

    @Test
    void findMyCurrentInterviewReturnsExistingSessionRegardlessOfCoverLetterStatus() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        CoverLetter coverLetter = saveReviewedCoverLetter("cl_1", "user_1", "rv_1", now);
        InterviewSession interviewSession = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        );
        interviewSession.activate();
        interviewSessionRepository.save(interviewSession);
        coverLetter.startReview(now.plusSeconds(180));
        coverLetterRepository.saveAndFlush(coverLetter);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        CurrentInterviewResult result = service.findMyCurrentInterview("cl_1");

        assertThat(result.coverLetterId()).isEqualTo("cl_1");
        assertThat(result.interviewSession().getId()).isEqualTo("is_1");
        assertThat(result.interviewSession().getInitialSourceReviewVersionId()).isEqualTo("rv_1");
        assertThat(result.interviewSession().getStatus()).isEqualTo(InterviewSessionStatus.ACTIVE);
        assertThat(result.interviewSession().getCreatedAt()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void findMyCurrentInterviewRejectsMissingOtherOwnerOrDeletedCoverLetter() {
        Instant now = Instant.parse("2026-07-10T01:00:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        coverLetterRepository.save(deleted);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertCurrentInterviewNotFound("cl_missing");
        assertCurrentInterviewNotFound("cl_other");
        assertCurrentInterviewNotFound("cl_deleted");
    }

    @Test
    void findMyInterviewQuestionsReturnsOrderedQuestionsAndOptionalThreadIds() {
        Instant now = Instant.parse("2026-07-11T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        InterviewQuestion secondQuestion = InterviewQuestion.create(
                "iq_2",
                interviewSession,
                "rv_2",
                2,
                InterviewQuestionType.TECHNICAL,
                "트랜잭션 격리 수준을 설명해 주세요."
        );
        InterviewQuestion firstQuestion = InterviewQuestion.create(
                "iq_1",
                interviewSession,
                "rv_1",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        );
        interviewQuestionRepository.saveAll(List.of(secondQuestion, firstQuestion));
        interviewThreadRepository.save(InterviewThread.active(
                "it_1",
                interviewSession,
                firstQuestion,
                now.plusSeconds(120)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        InterviewQuestionListResult result = service.findMyInterviewQuestions("is_1");

        assertThat(result.interviewSessionId()).isEqualTo("is_1");
        assertThat(result.items())
                .extracting(
                        InterviewQuestionItemResult::id,
                        InterviewQuestionItemResult::sourceReviewVersionId,
                        InterviewQuestionItemResult::order,
                        InterviewQuestionItemResult::type,
                        InterviewQuestionItemResult::question,
                        InterviewQuestionItemResult::threadId
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_1",
                                "rv_1",
                                1,
                                InterviewQuestionType.COVER_LETTER_BASED,
                                "프로젝트에서 맡은 역할을 설명해 주세요.",
                                "it_1"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_2",
                                "rv_2",
                                2,
                                InterviewQuestionType.TECHNICAL,
                                "트랜잭션 격리 수준을 설명해 주세요.",
                                null
                        )
                );
    }

    @Test
    void findMyInterviewQuestionsReturnsEmptyItemsWhenSessionHasNoQuestions() {
        Instant now = Instant.parse("2026-07-11T01:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(60)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        InterviewQuestionListResult result = service.findMyInterviewQuestions("is_1");

        assertThat(result.items()).isEmpty();
    }

    @Test
    void findMyInterviewQuestionsRejectsMissingOtherOwnerOrDeletedSession() {
        Instant now = Instant.parse("2026-07-11T01:00:00Z");
        CoverLetter otherOwner = coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_other",
                otherOwner,
                "rv_other",
                now.plusSeconds(60)
        ));
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(30));
        coverLetterRepository.save(deleted);
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_deleted",
                deleted,
                "rv_deleted",
                now.plusSeconds(60)
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertInterviewQuestionsNotFound("is_missing");
        assertInterviewQuestionsNotFound("is_other");
        assertInterviewQuestionsNotFound("is_deleted");
    }

    private void assertNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.startMyInterview(coverLetterId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void assertCurrentInterviewNotFound(String coverLetterId) {
        assertThatThrownBy(() -> service.findMyCurrentInterview(coverLetterId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void assertInterviewQuestionsNotFound(String interviewSessionId) {
        assertThatThrownBy(() -> service.findMyInterviewQuestions(interviewSessionId))
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
