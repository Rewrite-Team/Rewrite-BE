package com.daon.rewrite.global.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 두 SSE의 heartbeat 전송 간격이다. 프론트의 무응답 기준은 이 간격보다 길게 설정한다. */
@ConfigurationProperties("rewrite.sse")
public record SseProperties(@DefaultValue("15s") Duration heartbeatInterval) {

    public SseProperties {
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("rewrite.sse.heartbeat-interval must be positive");
        }
    }
}
