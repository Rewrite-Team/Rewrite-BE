package com.daon.rewrite.global.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 초기 스냅샷보다 변경 이벤트가 먼저 전송되지 않도록 연결별 초기화 버퍼를 둔다.
 * 호출자는 연결 등록 후 {@link #sendInitial(String, Object)}로 스냅샷을 보내고 {@link #finishInitialization()}을 호출한다.
 * 초기화 중 {@link #send(String, Object)}로 받은 이벤트는 메모리 큐에 보관했다가 스냅샷 다음에 전송한다.
 * 큐는 초기화 중인 단일 연결의 메모리에만 유지된다.
 */
public final class BufferedSseConnection {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final int MAX_BUFFERED_EVENTS = 256;

    private final SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
    private final Queue<Event> bufferedEvents = new ArrayDeque<>();
    private boolean initializing = true;
    private boolean closed;

    /**
     * emitter timeout은 30분이며 완료·시간초과·오류 콜백에서 라우터의 연결 등록을 정리한다.
     * cleanup은 여러 종료 콜백에서 호출되어도 안전해야 한다.
     */
    public BufferedSseConnection(Runnable cleanup) {
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            close();
            cleanup.run();
        });
        emitter.onError(error -> cleanup.run());
    }

    public SseEmitter emitter() {
        return emitter;
    }

    /** 초기 스냅샷은 변경 이벤트 큐를 거치지 않고 먼저 전송한다. */
    public synchronized void sendInitial(String name, Object data) {
        sendNow(new Event(name, data));
    }

    /** 초기화 중에는 큐에 넣고, 초기화가 끝난 연결에는 바로 전송한다. 닫힌 연결은 무시한다. */
    public synchronized void send(String name, Object data) {
        if (closed) {
            return;
        }
        Event event = new Event(name, data);
        if (initializing) {
            if (bufferedEvents.size() >= MAX_BUFFERED_EVENTS) {
                // 초기화가 지연되어 메모리가 계속 늘어나지 않도록 256개 한도를 넘는 연결을 닫는다.
                close();
                return;
            }
            bufferedEvents.add(event);
            return;
        }
        sendNow(event);
    }

    /** 같은 모니터로 새 send를 막고 큐에 들어온 순서대로 비운 뒤 실시간 전송으로 이어간다. */
    public synchronized void finishInitialization() {
        initializing = false;
        while (!bufferedEvents.isEmpty() && !closed) {
            sendNow(bufferedEvents.remove());
        }
    }

    /** 중복 종료는 무시한다. 라우터 정리는 emitter의 완료 콜백에 맡긴다. */
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        emitter.complete();
    }

    private void sendNow(Event event) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event.name()).data(event.data()));
        } catch (IOException | IllegalStateException exception) {
            // 클라이언트 단절 등 전송 실패는 이 연결을 종료하고 emitter의 오류 처리로 넘긴다.
            closed = true;
            emitter.completeWithError(exception);
        }
    }

    private record Event(String name, Object data) {
    }
}
