package com.daon.rewrite.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 외부 AI 호출 없이 실제 HTTP 스트림에서 초기 스냅샷 뒤의 반복 heartbeat를 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "rewrite.sse.heartbeat-interval=100ms")
@ActiveProfiles("test")
class SseHeartbeatHttpIntegrationTest {

    @Value("${local.server.port}") private int port;
    @Autowired private CoverLetterService coverLetters;
    @Autowired private LlmJobRepository jobs;

    @ParameterizedTest
    @ValueSource(strings = {"cover", "job"})
    void realHttpStreamsKeepSendingHeartbeatWithoutStateChanges(String kind) throws Exception {
        String coverId = coverLetters.create().getId();
        String path = "/cover-letters/stream";
        if (kind.equals("job")) {
            String jobId = "heartbeat_" + UUID.randomUUID();
            jobs.save(LlmJob.pendingReview(jobId, coverId, Instant.now(), 1));
            path = "/llm-jobs/" + jobId + "/stream";
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(3)).header("Accept", "text/event-stream").GET().build();
        try (HttpClient client = HttpClient.newHttpClient();
             var readerExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                    .startsWith("text/event-stream");
            try (InputStream body = response.body()) {
                List<Frame> frames = readerExecutor.submit(() -> readUntilTwoHeartbeats(body))
                        .get(3, TimeUnit.SECONDS);
                assertThat(frames.getFirst().event()).isEqualTo(
                        kind.equals("job") ? "job.state" : "cover-letter.review-status.snapshot");
                if (kind.equals("job")) {
                    assertThat(frames.get(1).event()).isEqualTo("review.questions");
                }
                assertThat(frames.stream().filter(frame -> frame.event().equals("heartbeat")).toList())
                        .hasSize(2).allSatisfy(frame -> assertThat(frame.data()).isEqualTo("{}"));
            }
        }
    }

    private List<Frame> readUntilTwoHeartbeats(InputStream body) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        List<Frame> frames = new ArrayList<>();
        int heartbeats = 0;
        String event = "message";
        StringBuilder data = new StringBuilder();
        for (String line; heartbeats < 2 && (line = reader.readLine()) != null;) {
            if (line.isEmpty() && !data.isEmpty()) {
                frames.add(new Frame(event, data.toString()));
                if (event.equals("heartbeat")) {
                    heartbeats++;
                }
                event = "message";
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = line.substring("event:".length()).stripLeading();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
        return frames;
    }

    private record Frame(String event, String data) {
    }
}
