package com.daon.rewrite.global.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class BufferedSseConnection {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final int MAX_BUFFERED_EVENTS = 256;

    private final SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
    private final Queue<Event> bufferedEvents = new ArrayDeque<>();
    private boolean initializing = true;
    private boolean closed;

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

    public synchronized void sendInitial(String name, Object data) {
        sendNow(new Event(name, data));
    }

    public synchronized void send(String name, Object data) {
        if (closed) {
            return;
        }
        Event event = new Event(name, data);
        if (initializing) {
            if (bufferedEvents.size() >= MAX_BUFFERED_EVENTS) {
                close();
                return;
            }
            bufferedEvents.add(event);
            return;
        }
        sendNow(event);
    }

    public synchronized void finishInitialization() {
        initializing = false;
        while (!bufferedEvents.isEmpty() && !closed) {
            sendNow(bufferedEvents.remove());
        }
    }

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
            closed = true;
            emitter.completeWithError(exception);
        }
    }

    private record Event(String name, Object data) {
    }
}
