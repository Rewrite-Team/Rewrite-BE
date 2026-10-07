package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.auth.DevCurrentUserProvider;
import com.daon.rewrite.auth.config.AuthSecurityConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * local-postgres의 profile 조합과 개발용 사용자·보안 체인, 인증 없는 API·Swagger 접근을 검증한다.
 * 접속 설정은 인메모리 H2로 덮어써 PostgreSQL 서버 없이 실행하며 PostgreSQL의 SQL 호환성은 검증하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rewrite-local-postgres-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("local-postgres")
class LocalPostgresProfileIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private Map<String, SecurityFilterChain> securityFilterChains;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void localPostgresComposesPostgresDatabaseAndDevelopmentAuthenticationProfiles() {
        assertThat(environment.getActiveProfiles())
                .contains("local-postgres", "db-postgres", "auth-dev")
                .doesNotContain("db-h2", "auth-real");
        assertThat(currentUserProvider).isInstanceOf(DevCurrentUserProvider.class);
        assertThat(securityFilterChains).containsOnlyKeys("devSecurityFilterChain");
        assertThat(applicationContext.getBeansOfType(AuthSecurityConfig.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(H2ConsoleSecurityConfig.class)).isEmpty();
    }

    @Test
    void localPostgresAllowsUserApiAndSwaggerWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user_dev_001"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
