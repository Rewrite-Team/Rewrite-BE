package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.global.sse.BufferedSseConnection;
import com.daon.rewrite.llmjob.dto.InterviewFeedbackDeltaResponse;
import com.daon.rewrite.llmjob.dto.LlmJobStateEventResponse;
import com.daon.rewrite.llmjob.dto.ReviewQuestionsEventResponse;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 모든 LLM Job의 상태와 도메인별 중간 결과를 SSE 연결에 전달한다.
 * 상태·첨삭 완료 문항은 DB를 다시 조회하고, 면접 피드백 조각은 Worker가 검증 후 전달한 내용을 사용한다.
 * 재연결은 현재 상태·완료 문항 스냅샷과 이 인스턴스에 남은 피드백 조각으로 복구한다.
 * Last-Event-ID 기반의 영속 이벤트 replay는 제공하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LlmJobStreamService {

    private static final String JOB_STATE_EVENT = "job.state";
    private static final String REVIEW_QUESTIONS_EVENT = "review.questions";
    private static final String INTERVIEW_FEEDBACK_DELTA_EVENT = "interview.feedback.delta";
    private static final int FEEDBACK_CHUNK_SIZE = 40;

    private final LlmJobService llmJobService;
    private final LlmJobRepository llmJobRepository;
    private final ReviewJobQuestionResultRepository questionResultRepository;

    // 한 Job을 여러 탭에서 구독할 수 있으므로 전송 이력은 연결별로 관리한다.
    private final Map<String, Map<String, JobConnection>> connections = new ConcurrentHashMap<>();
    // 초기화 버퍼와 별개인 Job별 재연결 자료다. 프로세스 메모리에만 보관하며 Job 종료 확인 시 제거한다.
    private final Map<String, List<InterviewFeedbackDeltaResponse>> feedbackBuffers = new ConcurrentHashMap<>();

    /**
     * 소유권을 확인한 Job에 연결을 등록하고 초기 상태·도메인 스냅샷을 먼저 전송한다.
     * 이미 종료된 Job도 최종 스냅샷을 보낸 뒤 연결을 닫는다.
     */
    public SseEmitter openMyJobStream(String jobId) {
        // 접근할 수 없는 Job은 연결을 등록하기 전에 거부한다.
        llmJobService.findMyJob(jobId);

        String connectionId = UUID.randomUUID().toString();
        BufferedSseConnection stream = new BufferedSseConnection(
                () -> removeConnection(jobId, connectionId)
        );
        JobConnection connection = new JobConnection(stream);
        synchronized (connection) {
            // 등록부터 초기화 완료까지 같은 연결의 polling·피드백 전송을 직렬화한다.
            connections.computeIfAbsent(jobId, ignored -> new ConcurrentHashMap<>())
                    .put(connectionId, connection);
            // 첫 소유권 조회와 연결 등록 사이에 진행된 상태도 초기 스냅샷에 반영한다.
            LlmJob job = llmJobService.findMyJob(jobId);
            sendInitial(connection, job);
            stream.finishInitialization();
        if (job.getStatus().isTerminal()) {
                stream.close();
            }
        }
        return stream.emitter();
    }

    /**
     * Worker가 전체 응답을 파싱·검증한 뒤 호출하며, 완성된 content를 화면 표시용 조각으로 전송한다.
     * 첫 delta는 전체 모델 응답을 검증한 이후에 전송된다.
     * 모든 조각을 먼저 보관해 동시 재연결도 1번부터 읽게 하고, 연결별 sequence로 중복 전송을 막는다.
     * 메시지 저장과 Job 완료는 이 메서드가 반환된 뒤 Worker의 확정 단계에서 수행한다.
     */
    public void publishValidatedFeedback(String jobId, String content) {
        List<InterviewFeedbackDeltaResponse> deltas = splitFeedback(content);
        feedbackBuffers.put(jobId, new CopyOnWriteArrayList<>(deltas));
        connections.getOrDefault(jobId, Map.of()).values()
                .forEach(connection -> {
                    synchronized (connection) {
                        deltas.stream()
                                .filter(delta -> connection.seenDeltaSequences().add(delta.sequence()))
                                .forEach(delta -> connection.stream().send(INTERVIEW_FEEDBACK_DELTA_EVENT, delta));
                    }
                });
    }

    /**
     * DB에 반영된 상태를 주기적으로 비교해 변경분을 전송한다.
     * 피드백 버퍼만 남은 Job도 조회해 구독자가 없어진 뒤 종료된 Job의 메모리를 정리한다.
     */
    @Scheduled(fixedDelay = 1000)
    void publishCommittedChanges() {
        Set<String> jobIds = ConcurrentHashMap.newKeySet();
        jobIds.addAll(connections.keySet());
        jobIds.addAll(feedbackBuffers.keySet());
        jobIds.forEach(this::publishCommittedChange);
    }

    private void publishCommittedChange(String jobId) {
        Map<String, JobConnection> jobConnections = connections.getOrDefault(jobId, Map.of());
        if (jobConnections.isEmpty()) {
            LlmJob job = llmJobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus().isTerminal()) {
                feedbackBuffers.remove(jobId);
            }
            return;
        }

        jobConnections.values().forEach(connection -> {
            synchronized (connection) {
                LlmJob job = llmJobRepository.findById(jobId).orElse(null);
                if (job == null) {
                    connection.stream().close();
                    return;
                }
                LlmJobStateEventResponse state = LlmJobStateEventResponse.from(job);
                boolean progressChanged = connection.lastState() == null
                        || connection.lastState().progress().current() != job.getProgressCurrent();
                List<ReviewJobQuestionResult> completedQuestions = isReviewJob(job) && progressChanged
                        ? findCompletedQuestions(jobId)
                        : List.of();
                // 완료 문항을 먼저 전송해야 뒤따르는 진행률이 화면에 반영된 문항보다 앞서지 않는다.
                publishNewQuestions(connection, completedQuestions);
                if (!state.equals(connection.lastState())) {
                    connection.stream().send(JOB_STATE_EVENT, state);
                    connection.lastState(state);
                }
                if (job.getStatus().isTerminal()) {
                    // 종료 상태를 반영한 뒤 닫는다. 완료 결과는 resultRef에 해당하는 도메인 API로 조회한다.
                    connection.stream().close();
                    feedbackBuffers.remove(jobId);
                }
            }
        });
    }

    /** job.state를 먼저 보내고 첨삭이면 완료 문항 전체를, 피드백 버퍼가 있으면 조각을 이어 보낸다. */
    private void sendInitial(JobConnection connection, LlmJob job) {
        LlmJobStateEventResponse state = LlmJobStateEventResponse.from(job);
        connection.stream().sendInitial(JOB_STATE_EVENT, state);
        connection.lastState(state);

        if (isReviewJob(job)) {
            List<ReviewJobQuestionResult> completedQuestions = findCompletedQuestions(job.getId());
            connection.stream().sendInitial(
                    REVIEW_QUESTIONS_EVENT,
                    ReviewQuestionsEventResponse.from(completedQuestions)
            );
            completedQuestions.forEach(result -> connection.seenQuestionIds().add(result.getQuestion().getId()));
        }
        // 면접 피드백 버퍼가 유실되면 delta 없이 상태만 복구하고 완료 후 저장된 최종 메시지를 조회한다.
        feedbackBuffers.getOrDefault(job.getId(), List.of()).stream()
                .filter(delta -> connection.seenDeltaSequences().add(delta.sequence()))
                .forEach(delta -> connection.stream().sendInitial(INTERVIEW_FEEDBACK_DELTA_EVENT, delta));
    }

    private void publishNewQuestions(
            JobConnection connection,
            List<ReviewJobQuestionResult> completedQuestions
    ) {
        completedQuestions.stream()
                .filter(result -> connection.seenQuestionIds().add(result.getQuestion().getId()))
                .forEach(result -> connection.stream().send(
                        REVIEW_QUESTIONS_EVENT,
                        ReviewQuestionsEventResponse.from(List.of(result))
                ));
    }

    private List<ReviewJobQuestionResult> findCompletedQuestions(String jobId) {
        return questionResultRepository.findByLlmJobIdAndStatusOrderByQuestionOrderAsc(
                jobId,
                ReviewJobQuestionResultStatus.COMPLETED
        );
    }

    /** code point 단위로 나눠 surrogate pair가 조각 경계에서 분리되지 않게 한다. sequence는 Job 안에서 1부터 시작한다. */
    private List<InterviewFeedbackDeltaResponse> splitFeedback(String content) {
        int[] codePoints = content.codePoints().toArray();
        List<InterviewFeedbackDeltaResponse> deltas = new ArrayList<>();
        for (int start = 0, sequence = 1; start < codePoints.length; start += FEEDBACK_CHUNK_SIZE, sequence++) {
            int length = Math.min(FEEDBACK_CHUNK_SIZE, codePoints.length - start);
            deltas.add(new InterviewFeedbackDeltaResponse(
                    sequence,
                    new String(codePoints, start, length)
            ));
        }
        return List.copyOf(deltas);
    }

    private boolean isReviewJob(LlmJob job) {
        return job.getType() == LlmJobType.COVER_LETTER_REVIEW
                || job.getType() == LlmJobType.COVER_LETTER_RE_REVIEW;
    }

    // emitter 종료 콜백에서 호출한다. 중복 호출을 허용하며 재연결용 피드백 버퍼는 주기 조회가 정리한다.
    private void removeConnection(String jobId, String connectionId) {
        Map<String, JobConnection> jobConnections = connections.get(jobId);
        if (jobConnections == null) {
            return;
        }
        jobConnections.remove(connectionId);
        if (jobConnections.isEmpty()) {
            connections.remove(jobId, jobConnections);
        }
    }

    /** 초기 스냅샷과 동시 발행의 중복을 걸러낼 이력을 보관한다. 재연결하면 새 이력으로 시작한다. */
    private static final class JobConnection {
        private final BufferedSseConnection stream;
        private final Set<String> seenQuestionIds = ConcurrentHashMap.newKeySet();
        private final Set<Integer> seenDeltaSequences = ConcurrentHashMap.newKeySet();
        private volatile LlmJobStateEventResponse lastState;

        private JobConnection(BufferedSseConnection stream) {
            this.stream = stream;
        }

        private BufferedSseConnection stream() {
            return stream;
        }

        private Set<String> seenQuestionIds() {
            return seenQuestionIds;
        }

        private Set<Integer> seenDeltaSequences() {
            return seenDeltaSequences;
        }

        private LlmJobStateEventResponse lastState() {
            return lastState;
        }

        private void lastState(LlmJobStateEventResponse lastState) {
            this.lastState = lastState;
        }
    }
}
