package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class ProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void localLoadsFileH2AndDevelopmentAuthenticationConfiguration() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    Environment environment = context.getEnvironment();

                    assertThat(environment.getActiveProfiles())
                            .contains("local", "db-h2", "auth-dev")
                            .doesNotContain("db-postgres", "auth-real", "internal-tools-secured");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .startsWith("jdbc:h2:file:./data/rewrite");
                    assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("org.h2.Driver");
                    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("update");
                    assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class))
                            .isTrue();
                });
    }

    @Test
    void localPostgresLoadsRequiredPostgresConnectionAndDevelopmentAuthenticationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local-postgres",
                        "DB_URL=jdbc:postgresql://127.0.0.1:5432/rewrite",
                        "DB_USERNAME=rewrite",
                        "DB_PASSWORD=rewrite-local"
                )
                .run(context -> {
                    Environment environment = context.getEnvironment();

                    assertThat(environment.getActiveProfiles())
                            .contains("local-postgres", "db-postgres", "auth-dev")
                            .doesNotContain("db-h2", "auth-real", "internal-tools-secured");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://127.0.0.1:5432/rewrite");
                    assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("org.postgresql.Driver");
                    assertThat(environment.getProperty("spring.datasource.username"))
                            .isEqualTo("rewrite");
                    assertThat(environment.getProperty("spring.datasource.password"))
                            .isEqualTo("rewrite-local");
                    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("update");
                    assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class))
                            .isFalse();
                });
    }

    @Test
    void prodLoadsPostgresAndProtectedAuthenticationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_URL=jdbc:postgresql://127.0.0.1:5432/rewrite",
                        "DB_USERNAME=rewrite_app",
                        "DB_PASSWORD=test-password"
                )
                .run(context -> {
                    Environment environment = context.getEnvironment();

                    assertThat(environment.getActiveProfiles())
                            .contains("prod", "db-postgres", "auth-real", "internal-tools-secured")
                            .doesNotContain("db-h2", "auth-dev");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://127.0.0.1:5432/rewrite");
                    assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("org.postgresql.Driver");
                    assertThat(environment.getProperty("spring.datasource.username"))
                            .isEqualTo("rewrite_app");
                    assertThat(environment.getProperty("spring.datasource.password"))
                            .isEqualTo("test-password");
                    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("update");
                    assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class))
                            .isFalse();
                });
    }
}
