package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.global.sse.BufferedSseConnection;
import com.daon.rewrite.llmjob.dto.InterviewFeedbackDeltaResponse;
import com.daon.rewrite.llmjob.dto.LlmJobStateEventResponse;
import com.daon.rewrite.llmjob.dto.ReviewQuestionsEventResponse;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
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

    private final Map<String, Map<String, JobConnection>> connections = new ConcurrentHashMap<>();
    private final Map<String, List<InterviewFeedbackDeltaResponse>> feedbackBuffers = new ConcurrentHashMap<>();

    public SseEmitter openMyJobStream(String jobId) {
        llmJobService.findMyJob(jobId);

        String connectionId = UUID.randomUUID().toString();
        BufferedSseConnection stream = new BufferedSseConnection(
                () -> removeConnection(jobId, connectionId)
        );
        JobConnection connection = new JobConnection(stream);
        synchronized (connection) {
            connections.computeIfAbsent(jobId, ignored -> new ConcurrentHashMap<>())
                    .put(connectionId, connection);
            LlmJob job = llmJobService.findMyJob(jobId);
            sendInitial(connection, job);
            stream.finishInitialization();
            if (isTerminal(job.getStatus())) {
                stream.close();
            }
        }
        return stream.emitter();
    }

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
            if (job == null || isTerminal(job.getStatus())) {
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
                publishNewQuestions(connection, completedQuestions);
                if (!state.equals(connection.lastState())) {
                    connection.stream().send(JOB_STATE_EVENT, state);
                    connection.lastState(state);
                }
                if (isTerminal(job.getStatus())) {
                    connection.stream().close();
                    feedbackBuffers.remove(jobId);
                }
            }
        });
    }

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

    private boolean isTerminal(LlmJobStatus status) {
        return status == LlmJobStatus.COMPLETED
                || status == LlmJobStatus.FAILED
                || status == LlmJobStatus.CANCELED;
    }

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
