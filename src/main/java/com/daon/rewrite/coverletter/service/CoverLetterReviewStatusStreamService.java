package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.dto.CoverLetterReviewStatusEventResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.sse.BufferedSseConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CoverLetterReviewStatusStreamService {

    private static final String SNAPSHOT_EVENT = "cover-letter.review-status.snapshot";
    private static final String CHANGED_EVENT = "cover-letter.review-status.changed";

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;

    private final Map<String, Map<String, CoverConnection>> connections = new ConcurrentHashMap<>();

    public SseEmitter openMyStream() {
        String ownerId = currentUserProvider.currentUser().id();
        String connectionId = UUID.randomUUID().toString();
        BufferedSseConnection stream = new BufferedSseConnection(
                () -> removeConnection(ownerId, connectionId)
        );
        CoverConnection connection = new CoverConnection(stream);
        synchronized (connection) {
            connections.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>())
                    .put(connectionId, connection);
            List<CoverLetter> coverLetters = findActiveCoverLetters(ownerId);
            connection.replaceStates(coverLetters);
            stream.sendInitial(
                    SNAPSHOT_EVENT,
                    CoverLetterReviewStatusEventResponse.Snapshot.from(coverLetters)
            );
            stream.finishInitialization();
        }
        return stream.emitter();
    }

    @Scheduled(fixedDelay = 1000)
    void publishCommittedChanges() {
        connections.forEach((ownerId, ownerConnections) -> {
            ownerConnections.values().forEach(connection -> {
                synchronized (connection) {
                    List<CoverLetter> coverLetters = findActiveCoverLetters(ownerId);
                    publishChanges(connection, coverLetters);
                }
            });
        });
    }

    private void publishChanges(CoverConnection connection, List<CoverLetter> coverLetters) {
        Map<String, CoverLetterReviewStatusEventResponse.Item> currentStates = toStateMap(coverLetters);
        currentStates.values().stream()
                .filter(state -> !state.equals(connection.states().get(state.coverLetterId())))
                .forEach(state -> connection.stream().send(CHANGED_EVENT, state));
        connection.states(currentStates);
    }

    private List<CoverLetter> findActiveCoverLetters(String ownerId) {
        return coverLetterRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);
    }

    private Map<String, CoverLetterReviewStatusEventResponse.Item> toStateMap(
            List<CoverLetter> coverLetters
    ) {
        Map<String, CoverLetterReviewStatusEventResponse.Item> states = new ConcurrentHashMap<>();
        coverLetters.stream()
                .map(CoverLetterReviewStatusEventResponse.Item::from)
                .forEach(state -> states.put(state.coverLetterId(), state));
        return states;
    }

    private void removeConnection(String ownerId, String connectionId) {
        Map<String, CoverConnection> ownerConnections = connections.get(ownerId);
        if (ownerConnections == null) {
            return;
        }
        ownerConnections.remove(connectionId);
        if (ownerConnections.isEmpty()) {
            connections.remove(ownerId, ownerConnections);
        }
    }

    private final class CoverConnection {
        private final BufferedSseConnection stream;
        private volatile Map<String, CoverLetterReviewStatusEventResponse.Item> states = Map.of();

        private CoverConnection(BufferedSseConnection stream) {
            this.stream = stream;
        }

        private BufferedSseConnection stream() {
            return stream;
        }

        private Map<String, CoverLetterReviewStatusEventResponse.Item> states() {
            return states;
        }

        private void states(Map<String, CoverLetterReviewStatusEventResponse.Item> states) {
            this.states = Map.copyOf(states);
        }

        private void replaceStates(List<CoverLetter> coverLetters) {
            states(toStateMap(coverLetters));
        }
    }
}
