package com.daon.rewrite.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.AuthProvider;
import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.controller.CoverLetterController;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.coverletter.service.CoverLetterReviewStatusStreamService;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.llmjob.controller.LlmJobController;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobAccessService;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 MVC SSE 응답을 읽고 예약 작업을 직접 진행해 heartbeat·순서·연결 종료를 대기 없이 검증한다. */
class SseHeartbeatTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private final CoverLetterRepository coverLetters = mock(CoverLetterRepository.class);
    private final LlmJobRepository jobs = mock(LlmJobRepository.class);
    private final ReviewJobQuestionResultRepository questions = mock(ReviewJobQuestionResultRepository.class);
    private final LlmJobAccessService jobService = mock(LlmJobAccessService.class);
    private final ThreadPoolTaskScheduler timer = mock(ThreadPoolTaskScheduler.class);
    private final List<HeartbeatTask> tasks = new ArrayList<>();
    private final List<MvcResult> requests = new ArrayList<>();
    private final AtomicBoolean broken = new AtomicBoolean();
    private SseHeartbeatScheduler heartbeatScheduler;
    private LlmJobStreamService jobStream;
    private LlmJob job;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(timer.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        when(timer.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    ScheduledFuture<?> future = mock(ScheduledFuture.class);
                    AtomicBoolean canceled = new AtomicBoolean();
                    when(future.cancel(false)).thenAnswer(ignored -> canceled.compareAndSet(false, true));
                    when(future.isCancelled()).thenAnswer(ignored -> canceled.get());
                    tasks.add(new HeartbeatTask(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), future));
                    return future;
                });
        heartbeatScheduler = new SseHeartbeatScheduler(new SseProperties(Duration.ofSeconds(15)), timer);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentUser("user_1", "사용자", null, AuthProvider.KAKAO, NOW));
        when(coverLetters.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc("user_1"))
                .thenReturn(List.of(CoverLetter.create("cover_1", "user_1", NOW)));
        job = LlmJob.pendingReview("job_1", "cover_1", NOW, 3);
        when(jobService.findMyJob("job_1")).thenAnswer(ignored -> job);
        when(questions.findByLlmJobIdAndStatusOrderByQuestionOrderAsc(any(), any())).thenReturn(List.of());
        var coverStream = new CoverLetterReviewStatusStreamService(users, coverLetters, heartbeatScheduler);
        jobStream = new LlmJobStreamService(jobService, jobs, questions, heartbeatScheduler);
        mvc = MockMvcBuilders.standaloneSetup(
                        new CoverLetterController(mock(CoverLetterService.class), coverStream,
                                mock(CoverLetterDetailQueryService.class)),
                        new LlmJobController(jobService, jobStream))
                .addFilters((request, response, chain) -> {
                    if (request.getParameter("broken") == null) {
                        chain.doFilter(request, response);
                        return;
                    }
                    chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response) {
                        @Override
                        public ServletOutputStream getOutputStream() throws IOException {
                            return new DelegatingServletOutputStream(super.getOutputStream()) {
                                @Override
                                public void write(int value) throws IOException {
                                    if (broken.get()) {
                                        throw new IOException("client disconnected");
                                    }
                                    super.write(value);
                                }
                            };
                        }
                    });
                })
                .build();
    }

    @AfterEach
    void closeRequests() {
        requests.forEach(result -> result.getRequest().getAsyncContext().complete());
        heartbeatScheduler.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cover-letters/stream", "/llm-jobs/job_1/stream"})
    void unchangedStreamsSendNamedHeartbeatAfterInitialSnapshotWithoutDatabaseReads(String path) throws Exception {
        MvcResult result = open(path);
        String initial = body(result);
        assertThat(initial).doesNotContain("event:heartbeat");
        assertThat(result.getRequest().getAsyncContext().getTimeout()).isEqualTo(30 * 60 * 1000L);
        HeartbeatTask task = tasks.getFirst();
        assertThat(task.firstRun()).isEqualTo(NOW.plusSeconds(15));
        assertThat(task.period()).isEqualTo(Duration.ofSeconds(15));
        clearInvocations(coverLetters, jobs, questions, jobService);

        task.tick().run();
        task.tick().run();

        assertThat(body(result)).isEqualTo(initial + "event:heartbeat\ndata:{}\n\n".repeat(2));
        verifyNoInteractions(coverLetters, jobs, questions, jobService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"completion", "timeout", "error"})
    void servletLifecycleStopsHeartbeat(String event) throws Exception {
        MvcResult result = open("/cover-letters/stream");
        MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
        if (event.equals("completion")) {
            context.complete();
        } else {
            for (var listener : context.getListeners()) {
                if (event.equals("timeout")) {
                    listener.onTimeout(new AsyncEvent(context));
                } else {
                    listener.onError(new AsyncEvent(context, new IOException("client disconnected")));
                }
            }
        }
        String before = body(result);

        tasks.getFirst().tick().run();

        assertThat(tasks.getFirst().future().isCancelled()).isTrue();
        assertThat(body(result)).isEqualTo(before);
    }

    @Test
    void disconnectedClientStopsItsTaskWhileAnotherConnectionKeepsReceivingHeartbeat() throws Exception {
        open("/cover-letters/stream?broken=true");
        MvcResult healthy = open("/cover-letters/stream");
        broken.set(true);

        tasks.get(0).tick().run();
        tasks.get(1).tick().run();

        assertThat(tasks.get(0).future().isCancelled()).isTrue();
        assertThat(tasks.get(1).future().isCancelled()).isFalse();
        assertThat(body(healthy)).endsWith("event:heartbeat\ndata:{}\n\n");
    }

    @Test
    void terminalJobReturnsItsSnapshotsWithoutStartingHeartbeat() throws Exception {
        job.cancel(NOW);

        MvcResult result = mvc.perform(get("/llm-jobs/job_1/stream"))
                .andExpect(status().isOk()).andReturn();

        assertThat(body(result)).contains("\"status\":\"CANCELED\"", "event:review.questions")
                .doesNotContain("event:heartbeat");
        assertThat(tasks).isEmpty();
    }

    @Test
    void reconnectReplaysFeedbackBeforeHeartbeat() throws Exception {
        job = LlmJob.pendingInterviewMessageFeedback("job_1", "cover_1", "message_1", NOW);
        job.startProcessing("피드백 생성 중");
        MvcResult original = open("/llm-jobs/job_1/stream");
        jobStream.publishValidatedFeedback("job_1", "피".repeat(45));
        original.getRequest().getAsyncContext().complete();

        MvcResult reconnected = open("/llm-jobs/job_1/stream");
        String replay = body(reconnected);
        assertThat(replay).contains("event:job.state", "\"sequence\":1", "\"sequence\":2")
                .doesNotContain("event:heartbeat");

        tasks.getLast().tick().run();

        assertThat(body(reconnected)).isEqualTo(replay + "event:heartbeat\ndata:{}\n\n");
    }

    private MvcResult open(String path) throws Exception {
        MvcResult result = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(request().asyncStarted()).andReturn();
        requests.add(result);
        return result;
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private record HeartbeatTask(Runnable tick, Instant firstRun, Duration period, ScheduledFuture<?> future) {
    }
}
