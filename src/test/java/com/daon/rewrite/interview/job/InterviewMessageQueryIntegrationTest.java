package com.daon.rewrite.interview.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackClient;
import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackMessage;
import com.daon.rewrite.interview.client.question.InterviewQuestionGenerationClient;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewMessageRepository;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewMessageQueryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final String QUESTION = "문제를 해결한 경험을 설명해 주세요.";
    private static final String FEEDBACK_CONTENT = "근거가 구체적입니다. 어떤 지표로 결과를 확인했나요?";

    @Autowired private MockMvc mockMvc;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private InterviewSessionRepository sessionRepository;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private InterviewThreadRepository threadRepository;
    @Autowired private InterviewMessageRepository messageRepository;
    @Autowired private LlmJobRepository jobRepository;
    @Autowired private InterviewMessageFeedbackJobTransactionService feedbackTransactionService;
    @Autowired private EntityManager entityManager;

    @MockitoBean private InterviewMessageFeedbackJobEventListener feedbackEventListener;
    @MockitoBean private InterviewQuestionGenerationJobEventListener questionEventListener;
    @MockitoBean private InterviewMessageFeedbackClient feedbackClient;
    @MockitoBean private InterviewQuestionGenerationClient questionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listsOnlyPublicFieldsInCreationAndIdOrderWithSeoulTimes() throws Exception {
        InterviewThread thread = createThread();
        InterviewMessage first = InterviewMessage.userAnswer("im_10", thread, "첫 답변", NOW);
        InterviewMessage assistant = InterviewMessage.assistantFeedback(
                "im_20", thread, FEEDBACK_CONTENT, "구체적인 근거", List.of("정량적 설명"),
                List.of("결과 지표 보완"), 87, "어떤 지표로 결과를 확인했나요?", NOW.plusSeconds(60)
        );
        InterviewMessage latest = InterviewMessage.userAnswer(
                "im_30", thread, "응답 시간을 측정했습니다.", NOW.plusSeconds(60)
        );
        messageRepository.saveAll(List.of(latest, assistant, first));
        LlmJob job = jobRepository.save(LlmJob.pendingInterviewMessageFeedback(
                "job_message_query", thread.getInterviewSession().getCoverLetter().getId(),
                latest.getId(), NOW.plusSeconds(120)
        ));
        entityManager.flush();
        entityManager.clear();

        JsonNode response = readMessages(thread.getId());

        assertThat(response.propertyStream().map(property -> property.getKey()).toList())
                .containsExactlyInAnyOrder("jobId", "items");
        assertThat(response.path("jobId").asText()).isEqualTo(job.getId());
        JsonNode items = response.path("items");
        assertThat(items.valueStream().map(item -> item.path("id").asText()).toList())
                .containsExactly("im_10", "im_20", "im_30");
        for (JsonNode item : items) {
            assertThat(item.propertyStream().map(property -> property.getKey()).toList())
                    .containsExactlyInAnyOrder("id", "role", "content", "score", "createdAt");
        }
        assertThat(items.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(items.get(0).path("content").asText()).isEqualTo("첫 답변");
        assertThat(items.get(0).path("score").isNull()).isTrue();
        assertThat(items.get(0).path("createdAt").asText()).isEqualTo("2026-07-28T09:00:00");
        assertThat(items.get(1).path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(items.get(1).path("content").asText()).isEqualTo(FEEDBACK_CONTENT);
        assertThat(items.get(1).path("score").isIntegralNumber()).isTrue();
        assertThat(items.get(1).path("score").asInt()).isEqualTo(87);
        assertThat(items.get(1).path("createdAt").asText()).isEqualTo("2026-07-28T09:01:00");
        assertThat(items.get(2).path("score").isNull()).isTrue();

        LlmJob completed = jobRepository.findById(job.getId()).orElseThrow();
        completed.markCompleted(1, "완료", LlmJobResultRefType.INTERVIEW_MESSAGE,
                assistant.getId(), NOW.plusSeconds(180));
        assertThat(readMessages(thread.getId()).path("jobId").isNull()).isTrue();
    }

    @Test
    void returnsEmptyItemsAndExplicitNullJobIdForThreadWithoutMessages() throws Exception {
        InterviewThread thread = createThread();

        JsonNode response = readMessages(thread.getId());

        assertThat(response.propertyStream().map(property -> property.getKey()).toList())
                .containsExactlyInAnyOrder("jobId", "items");
        assertThat(response.path("jobId").isNull()).isTrue();
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items").size()).isZero();
    }

    @Test
    void keepsInternalFeedbackStoredAndUsesFullContentInNextFeedbackRequest() throws Exception {
        InterviewThread thread = createThread();
        InterviewMessage first = InterviewMessage.userAnswer("im_10", thread, "첫 답변", NOW);
        InterviewMessage assistant = InterviewMessage.assistantFeedback(
                "im_20", thread, FEEDBACK_CONTENT, "구체적인 근거", List.of("정량적 설명"),
                List.of("결과 지표 보완"), 87, "어떤 지표로 결과를 확인했나요?", NOW.plusSeconds(60)
        );
        InterviewMessage latest = InterviewMessage.userAnswer(
                "im_30", thread, "응답 시간을 측정했습니다.", NOW.plusSeconds(120)
        );
        messageRepository.saveAll(List.of(first, assistant, latest));
        LlmJob job = jobRepository.save(LlmJob.pendingInterviewMessageFeedback(
                "job_message_context", thread.getInterviewSession().getCoverLetter().getId(),
                latest.getId(), NOW.plusSeconds(180)
        ));
        entityManager.flush();
        entityManager.clear();
        readMessages(thread.getId());

        InterviewMessage stored = messageRepository.findById(assistant.getId()).orElseThrow();
        assertThat(stored.getFeedbackSummary()).isEqualTo("구체적인 근거");
        assertThat(stored.getFeedbackStrengths()).containsExactly("정량적 설명");
        assertThat(stored.getFeedbackImprovements()).containsExactly("결과 지표 보완");
        assertThat(stored.getFollowUpQuestion()).isEqualTo("어떤 지표로 결과를 확인했나요?");

        InterviewMessageFeedbackWork work = feedbackTransactionService.start(job.getId());

        assertThat(work).isNotNull();
        assertThat(work.request().originalQuestion()).isEqualTo(QUESTION);
        assertThat(work.request().messages()).extracting(InterviewMessageFeedbackMessage::role)
                .containsExactly(InterviewMessageRole.USER, InterviewMessageRole.ASSISTANT,
                        InterviewMessageRole.USER);
        assertThat(work.request().messages()).extracting(InterviewMessageFeedbackMessage::content)
                .containsExactly("첫 답변", FEEDBACK_CONTENT, "응답 시간을 측정했습니다.");
        verifyNoInteractions(feedbackClient, questionClient, feedbackEventListener, questionEventListener);
    }

    private InterviewThread createThread() {
        CoverLetter coverLetter = coverLetterRepository.save(
                CoverLetter.create("cl_message_query", "user_dev_001", NOW)
        );
        InterviewSession session = InterviewSession.questionGenerating(
                "is_message_query", coverLetter, "rv_message_query", NOW
        );
        session.activate();
        session = sessionRepository.save(session);
        InterviewQuestion question = questionRepository.save(InterviewQuestion.create(
                "iq_message_query", session, "rv_message_query", 1,
                InterviewQuestionType.COVER_LETTER_BASED, QUESTION
        ));
        return threadRepository.save(InterviewThread.active("it_message_query", session, question, NOW));
    }

    private JsonNode readMessages(String threadId) throws Exception {
        String body = mockMvc.perform(get("/interview-threads/{threadId}/messages", threadId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
