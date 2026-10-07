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

/**
 * 메인 목록을 갱신하는 사용자별 SSE 연결을 관리하고 활성 자기소개서의 현재 상태를 전송한다.
 * 1초 주기로 DB를 조회해 연결별 직전 스냅샷과 비교하므로, Job 내부 진행률 대신 표시 상태와 최신 성공 버전의 변화를 전달한다.
 * 연결과 비교용 상태는 현재 프로세스 메모리에 보관하며 재연결 시 새 전체 스냅샷으로 복구한다.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterReviewStatusStreamService {

    private static final String SNAPSHOT_EVENT = "cover-letter.review-status.snapshot";
    private static final String CHANGED_EVENT = "cover-letter.review-status.changed";

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;

    // ownerId → connectionId별 연결이다. 여러 탭의 연결은 각각 직전 전송 상태를 유지한다.
    private final Map<String, Map<String, CoverConnection>> connections = new ConcurrentHashMap<>();

    /**
     * 현재 사용자의 연결을 먼저 등록하고 페이지와 무관한 모든 활성 자기소개서의 스냅샷을 전송한다.
     * 연결 모니터로 초기 스냅샷과 후속 변경 비교를 직렬화하고, 초기 전송 후 BufferedSseConnection의 초기화를 마친다.
     * 연결 완료·시간초과·오류 시에는 생성 시 전달한 콜백으로 라우팅 등록을 제거한다.
     */
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

    /**
     * 열린 연결마다 DB의 현재 커밋 상태를 다시 읽어 직전 상태와 달라진 항목을 보낸다.
     * 같은 연결의 초기화·변경 전송은 직렬화하며, 조회 사이에 거친 모든 중간 상태를 재생하는 방식은 아니다.
     */
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

    /**
     * 새로 관측되거나 표시 상태·최신 성공 버전이 바뀐 항목만 단건 이벤트로 전송한다.
     * 삭제된 항목은 비교 상태에서 제외하며 삭제 이벤트는 별도로 만들지 않는다.
     */
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

    /** 종료된 연결을 제거하고, 해당 사용자의 마지막 연결이면 사용자 라우팅 항목도 정리한다. */
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
