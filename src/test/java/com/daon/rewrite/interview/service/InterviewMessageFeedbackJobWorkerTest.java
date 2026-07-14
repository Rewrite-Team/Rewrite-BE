package com.daon.rewrite.interview.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackClient;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackClientException;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackMessage;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackRequest;
import com.daon.rewrite.interview.client.InterviewMessageFeedbackResult;
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
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
class InterviewMessageFeedbackJobWorkerTest {

    @Autowired
    private InterviewMessageFeedbackJobWorker worker;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewThreadRepository interviewThreadRepository;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @MockitoBean
    private InterviewMessageFeedbackClient client;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        interviewMessageRepository.deleteAll();
        interviewThreadRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        llmJobRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void executeCompletesPendingJobAndStoresAssistantFeedback() {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        InterviewThread thread = saveActiveThread(now);
        saveConversationAndPendingJob(thread, now);
        InterviewMessageFeedbackResult feedback = feedbackResult();
        given(client.generate(any())).willReturn(feedback);
        given(idGenerator.generate("im")).willReturn("im_feedback");
        given(clock.instant()).willReturn(now.plusSeconds(300));

        worker.execute("job_1");

        ArgumentCaptor<InterviewMessageFeedbackRequest> requestCaptor =
                ArgumentCaptor.forClass(InterviewMessageFeedbackRequest.class);
        then(client).should().generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().originalQuestion())
                .isEqualTo("프로젝트에서 맡은 역할을 설명해 주세요.");
        assertThat(requestCaptor.getValue().messages())
                .extracting(InterviewMessageFeedbackMessage::role, InterviewMessageFeedbackMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(InterviewMessageRole.USER, "첫 번째 답변"),
                        org.assertj.core.groups.Tuple.tuple(InterviewMessageRole.ASSISTANT, "첫 번째 피드백"),
                        org.assertj.core.groups.Tuple.tuple(InterviewMessageRole.USER, "보완한 답변")
                );

        assertThat(interviewMessageRepository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .hasSize(4)
                .last()
                .satisfies(message -> {
                    assertThat(message.getId()).isEqualTo("im_feedback");
                    assertThat(message.getRole()).isEqualTo(InterviewMessageRole.ASSISTANT);
                    assertThat(message.getContent()).isEqualTo(feedback.content());
                    assertThat(message.getFeedbackSummary()).isEqualTo(feedback.feedbackSummary());
                    assertThat(message.getFeedbackStrengths()).containsExactlyElementsOf(feedback.feedbackStrengths());
                    assertThat(message.getFeedbackImprovements())
                            .containsExactlyElementsOf(feedback.feedbackImprovements());
                    assertThat(message.getScore()).isEqualTo(feedback.score());
                    assertThat(message.getFollowUpQuestion()).isEqualTo(feedback.followUpQuestion());
                    assertThat(message.getCreatedAt()).isEqualTo(now.plusSeconds(300));
                });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getProgressCurrent()).isEqualTo(1);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.INTERVIEW_MESSAGE);
            assertThat(job.getResultRefId()).isEqualTo("im_feedback");
            assertThat(job.getCompletedAt()).isEqualTo(now.plusSeconds(300));
        });
    }

    @Test
    void executeFailsJobWithoutAssistantMessageWhenProviderFails() {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        InterviewThread thread = saveActiveThread(now);
        saveConversationAndPendingJob(thread, now);
        given(client.generate(any())).willThrow(
                InterviewMessageFeedbackClientException.providerError(new IllegalStateException("provider down"))
        );
        given(clock.instant()).willReturn(now.plusSeconds(300));

        worker.execute("job_1");

        assertThat(interviewMessageRepository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .extracting(InterviewMessage::getId)
                .containsExactly("im_1", "im_2", "im_3");
        assertFailedJob("LLM_PROVIDER_ERROR", "LLM 응답 생성에 실패했습니다.", now.plusSeconds(300));
    }

    @Test
    void executeFailsJobWithoutAssistantMessageWhenOutputValidationFails() {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        InterviewThread thread = saveActiveThread(now);
        saveConversationAndPendingJob(thread, now);
        given(client.generate(any()))
                .willThrow(InterviewMessageFeedbackClientException.outputValidationFailed());
        given(clock.instant()).willReturn(now.plusSeconds(300));

        worker.execute("job_1");

        assertThat(interviewMessageRepository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .extracting(InterviewMessage::getId)
                .containsExactly("im_1", "im_2", "im_3");
        assertFailedJob(
                "LLM_OUTPUT_VALIDATION_FAILED",
                "LLM 출력 형식이 올바르지 않습니다.",
                now.plusSeconds(300)
        );
    }

    @Test
    void executeFailsJobWhenRequestReferenceDoesNotPointToUserMessage() {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        InterviewThread thread = saveActiveThread(now);
        InterviewMessage assistantMessage = interviewMessageRepository.save(InterviewMessage.assistantFeedback(
                "im_assistant",
                thread,
                "피드백",
                "요약",
                List.of("강점"),
                List.of("개선점"),
                70,
                "꼬리질문",
                now.plusSeconds(60)
        ));
        llmJobRepository.save(LlmJob.pendingInterviewMessageFeedback(
                "job_1",
                "cl_1",
                assistantMessage.getId(),
                now.plusSeconds(120)
        ));
        given(clock.instant()).willReturn(now.plusSeconds(300));

        worker.execute("job_1");

        then(client).shouldHaveNoInteractions();
        assertThat(interviewMessageRepository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .extracting(InterviewMessage::getId)
                .containsExactly("im_assistant");
        assertFailedJob("INTERNAL_ERROR", "면접 답변 피드백 처리 중 오류가 발생했습니다.", now.plusSeconds(300));
    }

    @Test
    void executeSkipsCompletedJobWithoutDuplicatingAssistantMessage() {
        Instant now = Instant.parse("2026-07-14T01:00:00Z");
        InterviewThread thread = saveActiveThread(now);
        saveConversationAndPendingJob(thread, now);
        given(client.generate(any())).willReturn(feedbackResult());
        given(idGenerator.generate("im")).willReturn("im_feedback");
        given(clock.instant()).willReturn(now.plusSeconds(300));

        worker.execute("job_1");
        worker.execute("job_1");

        then(client).should(times(1)).generate(any());
        assertThat(interviewMessageRepository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .extracting(InterviewMessage::getId)
                .containsExactly("im_1", "im_2", "im_3", "im_feedback");
    }

    private InterviewThread saveActiveThread(Instant now) {
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession session = InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(10)
        );
        session.activate();
        interviewSessionRepository.save(session);
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.create(
                "iq_1",
                session,
                "rv_1",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        ));
        return interviewThreadRepository.save(InterviewThread.active(
                "it_1",
                session,
                question,
                now.plusSeconds(20)
        ));
    }

    private void saveConversationAndPendingJob(InterviewThread thread, Instant now) {
        interviewMessageRepository.saveAll(List.of(
                InterviewMessage.userAnswer("im_1", thread, "첫 번째 답변", now.plusSeconds(30)),
                InterviewMessage.assistantFeedback(
                        "im_2",
                        thread,
                        "첫 번째 피드백",
                        "첫 번째 요약",
                        List.of("강점"),
                        List.of("개선점"),
                        70,
                        "보완해서 답변해 주세요.",
                        now.plusSeconds(40)
                ),
                InterviewMessage.userAnswer("im_3", thread, "보완한 답변", now.plusSeconds(50))
        ));
        llmJobRepository.save(LlmJob.pendingInterviewMessageFeedback(
                "job_1",
                "cl_1",
                "im_3",
                now.plusSeconds(60)
        ));
    }

    private InterviewMessageFeedbackResult feedbackResult() {
        return new InterviewMessageFeedbackResult(
                "역할은 명확하지만 성과 설명을 더 보강하면 좋습니다.",
                "성과와 의사결정 근거를 보강해야 합니다.",
                List.of("담당 역할을 구체적으로 언급했습니다."),
                List.of("성과 지표를 추가하세요."),
                78,
                "가장 중요하게 고려한 트레이드오프는 무엇이었나요?"
        );
    }

    private void assertFailedJob(String errorCode, String errorMessage, Instant failedAt) {
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo(errorCode);
            assertThat(job.getErrorMessage()).isEqualTo(errorMessage);
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
            assertThat(job.getResultRefType()).isNull();
            assertThat(job.getResultRefId()).isNull();
        });
    }
}
