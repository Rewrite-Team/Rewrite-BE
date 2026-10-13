package com.daon.rewrite.global.sse;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/** DB polling·AI 실행의 scheduler와 분리해 연결별 heartbeat를 예약한다. */
@Component
@EnableConfigurationProperties(SseProperties.class)
public class SseHeartbeatScheduler implements AutoCloseable {

    private final Duration interval;
    private final ThreadPoolTaskScheduler scheduler;

    @Autowired
    public SseHeartbeatScheduler(SseProperties properties) {
        this(properties, new ThreadPoolTaskScheduler());
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sse-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
    }

    SseHeartbeatScheduler(SseProperties properties, ThreadPoolTaskScheduler scheduler) {
        this.interval = properties.heartbeatInterval();
        this.scheduler = scheduler;
    }

    ScheduledFuture<?> schedule(Runnable heartbeat) {
        return scheduler.scheduleAtFixedRate(
                heartbeat,
                scheduler.getClock().instant().plus(interval),
                interval
        );
    }

    @PreDestroy
    @Override
    public void close() {
        scheduler.shutdown();
    }
}
