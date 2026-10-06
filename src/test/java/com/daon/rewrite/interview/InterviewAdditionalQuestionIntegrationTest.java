package com.daon.rewrite.interview;

import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.job.InterviewQuestionGenerationJobWorker;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class InterviewAdditionalQuestionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private CurrentUserProvider currentUserProvider;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private ReviewVersionRepository reviewVersionRepository;
    @Autowired private InterviewSessionRepository interviewSessionRepository;
    @Autowired private LlmJobRepository llmJobRepository;
    @Autowired private ApplicationEvents applicationEvents;

    @MockitoBean private InterviewQuestionGenerationJobWorker worker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void additionalQuestionUsesLatestSuccessfulVersionInsteadOfInitialSessionVersion() throws Exception {
        ActiveInterview interview = createActiveInterview(currentUserProvider.currentUser().id());

        String jobId = requestAdditionalQuestion(interview.sessionId());

        LlmJob job = llmJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getType()).isEqualTo(LlmJobType.INTERVIEW_ADDITIONAL_QUESTION_GENERATION);
        assertThat(job.getStatus()).isEqualTo(LlmJobStatus.PENDING);
        assertThat(job.getTargetId()).isEqualTo(interview.coverLetterId());
        assertThat(job.getRequestRefType()).isEqualTo(LlmJobRequestRefType.REVIEW_VERSION);
        assertThat(job.getRequestRefId()).isEqualTo(interview.latestVersionId());
        assertThat(interviewSessionRepository.findById(interview.sessionId()).orElseThrow()
                .getInitialSourceReviewVersionId()).isEqualTo(interview.initialVersionId());
        assertThat(applicationEvents.stream(LlmJobCreatedEvent.class).map(LlmJobCreatedEvent::jobId).toList())
                .containsExactly(jobId);
    }

    @Test
    void failedNewerReviewAttemptKeepsLatestSuccessfulVersionAsSource() throws Exception {
        ActiveInterview interview = createActiveInterview(currentUserProvider.currentUser().id());
        CoverLetter coverLetter = coverLetterRepository.findById(interview.coverLetterId()).orElseThrow();
        Instant failedAt = NOW.plusSeconds(2);
        LlmJob failedJob = LlmJob.pendingReReview(
                id("job"), coverLetter.getId(), null, interview.latestVersionId(), failedAt, 1
        );
        failedJob.markFailed(0, "첨삭 실패", "LLM_PROVIDER_ERROR", "첨삭 실패", failedAt);
        failedJob = llmJobRepository.save(failedJob);
        reviewVersionRepository.save(ReviewVersion.started(
                id("rv"), coverLetter, "v0.3", null, failedJob, failedAt
        ));
        coverLetter.failReview(failedAt);
        coverLetterRepository.save(coverLetter);

        String jobId = requestAdditionalQuestion(interview.sessionId());

        assertThat(llmJobRepository.findById(jobId).orElseThrow().getRequestRefId())
                .isEqualTo(interview.latestVersionId());
    }

    @ParameterizedTest
    @EnumSource(value = LlmJobStatus.class, names = {"PENDING", "PROCESSING"})
    void repeatedRequestReusesRunningJobWithoutPublishingAnotherEvent(LlmJobStatus runningStatus) throws Exception {
        ActiveInterview interview = createActiveInterview(currentUserProvider.currentUser().id());
        String firstJobId = requestAdditionalQuestion(interview.sessionId());
        if (runningStatus == LlmJobStatus.PROCESSING) {
            LlmJob job = llmJobRepository.findById(firstJobId).orElseThrow();
            job.startProcessing("질문 생성 중");
            llmJobRepository.save(job);
        }
        long jobCount = llmJobRepository.count();

        String repeatedJobId = requestAdditionalQuestion(interview.sessionId());

        assertThat(repeatedJobId).isEqualTo(firstJobId);
        assertThat(llmJobRepository.count()).isEqualTo(jobCount);
        assertThat(llmJobRepository.findById(firstJobId).orElseThrow().getStatus()).isEqualTo(runningStatus);
        assertThat(applicationEvents.stream(LlmJobCreatedEvent.class).map(LlmJobCreatedEvent::jobId).toList())
                .containsExactly(firstJobId);
    }

    @Test
    void missingLatestReviewVersionReturnsNotFoundEvenWithRunningJob() throws Exception {
        ActiveInterview interview = createActiveInterview(currentUserProvider.currentUser().id());
        llmJobRepository.save(LlmJob.pendingAdditionalInterviewQuestionGeneration(
                id("job"), interview.coverLetterId(), interview.latestVersionId(), NOW
        ));
        CoverLetter coverLetter = coverLetterRepository.findById(interview.coverLetterId()).orElseThrow();
        coverLetter.completeReview(id("missing-rv"), NOW);
        coverLetterRepository.save(coverLetter);

        assertNotFoundWithoutCreatingJob(interview.sessionId());
    }

    @Test
    void latestReviewVersionFromAnotherCoverLetterReturnsNotFoundWithoutCreatingJob() throws Exception {
        String ownerId = currentUserProvider.currentUser().id();
        ActiveInterview interview = createActiveInterview(ownerId);
        ActiveInterview otherInterview = createActiveInterview(ownerId);
        CoverLetter coverLetter = coverLetterRepository.findById(interview.coverLetterId()).orElseThrow();
        coverLetter.completeReview(otherInterview.latestVersionId(), NOW);
        coverLetterRepository.save(coverLetter);

        assertNotFoundWithoutCreatingJob(interview.sessionId());
    }

    @Test
    void anotherUsersSessionReturnsNotFoundWithoutCreatingJob() throws Exception {
        ActiveInterview interview = createActiveInterview("another-user");

        assertNotFoundWithoutCreatingJob(interview.sessionId());
    }

    private String requestAdditionalQuestion(String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/interviews/{interviewSessionId}/questions", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").isString())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.size()).isEqualTo(1);
        return response.path("jobId").asText();
    }

    private void assertNotFoundWithoutCreatingJob(String sessionId) throws Exception {
        long jobCount = llmJobRepository.count();

        mockMvc.perform(post("/interviews/{interviewSessionId}/questions", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(llmJobRepository.count()).isEqualTo(jobCount);
        assertThat(applicationEvents.stream(LlmJobCreatedEvent.class).toList()).isEmpty();
    }

    private ActiveInterview createActiveInterview(String ownerId) {
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.create(id("cl"), ownerId, NOW));
        ReviewVersion initialVersion = reviewVersionRepository.save(ReviewVersion.started(
                id("rv"), coverLetter, "v0.1", null, null, NOW
        ));
        ReviewVersion latestVersion = reviewVersionRepository.save(ReviewVersion.started(
                id("rv"), coverLetter, "v0.2", null, null, NOW.plusSeconds(1)
        ));
        coverLetter.completeReview(latestVersion.getId(), NOW.plusSeconds(1));
        coverLetterRepository.save(coverLetter);
        InterviewSession session = InterviewSession.questionGenerating(
                id("is"), coverLetter, initialVersion.getId(), NOW
        );
        session.activate();
        interviewSessionRepository.save(session);
        return new ActiveInterview(coverLetter.getId(), session.getId(), initialVersion.getId(), latestVersion.getId());
    }

    private String id(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private record ActiveInterview(String coverLetterId, String sessionId, String initialVersionId, String latestVersionId) {
    }
}
