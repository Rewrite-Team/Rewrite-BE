package com.daon.rewrite;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewThread;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PostgreSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("rewrite")
            .withUsername("rewrite")
            .withPassword("rewrite");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsJsonFeedbackWithPostgresql() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }

        Instant now = Instant.parse("2026-09-07T00:00:00Z");
        CoverLetter coverLetter = CoverLetter.create("cover-letter-id", "owner-id", now);
        InterviewSession session = InterviewSession.questionGenerating(
                "session-id",
                coverLetter,
                "review-version-id",
                now
        );
        InterviewQuestion question = InterviewQuestion.create(
                "question-id",
                session,
                "review-version-id",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "질문"
        );
        InterviewThread thread = InterviewThread.active("thread-id", session, question, now);
        InterviewMessage message = InterviewMessage.assistantFeedback(
                "message-id",
                thread,
                "피드백",
                "요약",
                List.of("강점"),
                List.of("개선점"),
                80,
                "후속 질문",
                now
        );

        entityManager.persist(coverLetter);
        entityManager.persist(session);
        entityManager.persist(question);
        entityManager.persist(thread);
        entityManager.persist(message);
        entityManager.flush();
        entityManager.clear();

        InterviewMessage saved = entityManager.find(InterviewMessage.class, "message-id");

        assertEquals(List.of("강점"), saved.getFeedbackStrengths());
        assertEquals(List.of("개선점"), saved.getFeedbackImprovements());
        assertEquals(now, saved.getCreatedAt());
    }
}
