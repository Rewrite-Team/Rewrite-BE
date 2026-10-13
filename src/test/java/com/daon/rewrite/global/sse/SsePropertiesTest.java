package com.daon.rewrite.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SsePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultIntervalIsFifteenSeconds() {
        contextRunner.run(context -> assertThat(context.getBean(SseProperties.class).heartbeatInterval())
                .isEqualTo(Duration.ofSeconds(15)));
    }

    @Test
    void configuredIntervalOverridesDefault() {
        contextRunner.withPropertyValues("rewrite.sse.heartbeat-interval=3s")
                .run(context -> assertThat(context.getBean(SseProperties.class).heartbeatInterval())
                        .isEqualTo(Duration.ofSeconds(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void nonPositiveIntervalPreventsStartup(String interval) {
        contextRunner.withPropertyValues("rewrite.sse.heartbeat-interval=" + interval)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("rewrite.sse.heartbeat-interval must be positive"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SseProperties.class)
    static class PropertiesConfiguration {
    }
}
