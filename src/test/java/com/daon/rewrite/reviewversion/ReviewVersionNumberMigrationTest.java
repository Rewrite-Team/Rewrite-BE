package com.daon.rewrite.reviewversion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 기존 파일 DB와 실제 수동 이관 SQL을 사용해 값·참조·최종 제약을 검증한다. */
class ReviewVersionNumberMigrationTest {

    @TempDir Path databaseDirectory;

    private Connection connection;
    private String databaseUrl;

    @BeforeEach
    void createLegacyDatabase() throws SQLException {
        databaseUrl = "jdbc:h2:file:" + databaseDirectory.resolve("rewrite")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        connection = DriverManager.getConnection(databaseUrl, "sa", "");
        execute("""
                CREATE TABLE cover_letters (
                    id VARCHAR(64) PRIMARY KEY,
                    latest_review_version_id VARCHAR(64)
                )
                """);
        execute("""
                CREATE TABLE llm_jobs (
                    id VARCHAR(64) PRIMARY KEY,
                    request_ref_id VARCHAR(64),
                    result_ref_id VARCHAR(64)
                )
                """);
        // overflow·비정상 import 값도 검증하기 위해 기존 선언(20)보다 문자열 길이를 넓게 둔다.
        execute("""
                CREATE TABLE review_versions (
                    id VARCHAR(64) PRIMARY KEY,
                    cover_letter_id VARCHAR(64) NOT NULL REFERENCES cover_letters(id),
                    version VARCHAR(64) NOT NULL,
                    llm_job_id VARCHAR(64) UNIQUE REFERENCES llm_jobs(id),
                    request_instruction VARCHAR(1000),
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_review_versions_cover_letter_version UNIQUE (cover_letter_id, version)
                )
                """);
        execute("""
                CREATE TABLE review_version_question_results (
                    id VARCHAR(64) PRIMARY KEY,
                    review_version_id VARCHAR(64) NOT NULL REFERENCES review_versions(id)
                )
                """);
        execute("""
                CREATE TABLE interview_sessions (
                    id VARCHAR(64) PRIMARY KEY,
                    initial_source_review_version_id VARCHAR(64) NOT NULL REFERENCES review_versions(id)
                )
                """);
        execute("INSERT INTO cover_letters (id) VALUES ('cl_1'), ('cl_2')");
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void preservesNumbersAndReferencesAfterReconnectingToMigratedFile() throws SQLException {
        execute("INSERT INTO llm_jobs VALUES ('job_10', 'rv_9', 'rv_10')");
        insertLegacyVersion("rv_1", "cl_1", "v0.1", null);
        insertLegacyVersion("rv_3", "cl_1", "v0.3", null);
        insertLegacyVersion("rv_9", "cl_1", "v0.9", null);
        insertLegacyVersion("rv_10", "cl_1", "v0.10", "job_10");
        insertLegacyVersion("rv_other", "cl_2", "v0.3", null);
        execute("UPDATE cover_letters SET latest_review_version_id = 'rv_9' WHERE id = 'cl_1'");
        execute("INSERT INTO review_version_question_results VALUES ('result_9', 'rv_9')");
        execute("INSERT INTO interview_sessions VALUES ('interview_9', 'rv_9')");
        var versionFields = rows("SELECT id, cover_letter_id, llm_job_id, request_instruction, created_at FROM review_versions ORDER BY id");
        var coverLetters = rows("SELECT * FROM cover_letters ORDER BY id");
        var jobs = rows("SELECT * FROM llm_jobs ORDER BY id");
        var results = rows("SELECT * FROM review_version_question_results ORDER BY id");
        var interviews = rows("SELECT * FROM interview_sessions ORDER BY id");

        migrate();
        connection.close();
        connection = DriverManager.getConnection(databaseUrl + ";IFEXISTS=TRUE", "sa", "");

        assertThat(rows("SELECT id, version_number FROM review_versions WHERE cover_letter_id = 'cl_1' ORDER BY version_number"))
                .containsExactly(
                        List.of("rv_1", 1L), List.of("rv_3", 3L), List.of("rv_9", 9L), List.of("rv_10", 10L)
                );
        assertThat(rows("SELECT id, version_number FROM review_versions WHERE cover_letter_id = 'cl_2'"))
                .containsExactly(List.of("rv_other", 3L));
        assertThat(rows("SELECT id, cover_letter_id, llm_job_id, request_instruction, created_at FROM review_versions ORDER BY id"))
                .isEqualTo(versionFields);
        assertThat(rows("SELECT * FROM cover_letters ORDER BY id")).isEqualTo(coverLetters);
        assertThat(rows("SELECT * FROM llm_jobs ORDER BY id")).isEqualTo(jobs);
        assertThat(rows("SELECT * FROM review_version_question_results ORDER BY id")).isEqualTo(results);
        assertThat(rows("SELECT * FROM interview_sessions ORDER BY id")).isEqualTo(interviews);
        assertThat(rows("SELECT MAX(version_number) FROM review_versions WHERE cover_letter_id = 'cl_1'"))
                .containsExactly(List.of(10L));
        assertFinalSchema();
    }

    @Test
    void migratesEmptyVersionTable() throws SQLException {
        migrate();

        assertThat(rows("SELECT COUNT(*) FROM review_versions")).containsExactly(List.of(0L));
        assertFinalSchema();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "v0.0", "v0.-1", "v0.01", "v0.+1", "v1.1", "v0.a", "v0.1.2", " v0.1", "v0.1 ", "v0.1\n",
            "v0.9223372036854775808", "v0.99999999999999999999999999999999999999999999999999"
    })
    void invalidLegacyNumberStopsBeforeAnySchemaOrDataChange(String version) throws SQLException {
        if (version == null) {
            execute("ALTER TABLE review_versions ALTER COLUMN version DROP NOT NULL");
        }
        insertLegacyVersion("rv_invalid", "cl_1", version, null);
        var before = rows("SELECT * FROM review_versions ORDER BY id");

        assertThatThrownBy(this::migrate)
                .isInstanceOf(ScriptException.class)
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(rows("SELECT * FROM review_versions ORDER BY id")).isEqualTo(before);
        assertThat(rows("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'review_versions' AND column_name = 'version_number'
                """)).isEmpty();
        assertThat(rows("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_name = 'review_versions' AND constraint_name = 'uk_review_versions_cover_letter_version'
                """)).hasSize(1);
    }

    @Test
    void duplicateLegacyNumbersStopBeforeSchemaChangeEvenWhenOldUniqueIsMissing() throws SQLException {
        execute("ALTER TABLE review_versions DROP CONSTRAINT uk_review_versions_cover_letter_version");
        insertLegacyVersion("rv_first", "cl_1", "v0.3", null);
        insertLegacyVersion("rv_duplicate", "cl_1", "v0.3", null);
        var before = rows("SELECT * FROM review_versions ORDER BY id");

        assertThatThrownBy(this::migrate)
                .isInstanceOf(ScriptException.class)
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(rows("SELECT * FROM review_versions ORDER BY id")).isEqualTo(before);
        assertThat(rows("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'review_versions' AND column_name = 'version_number'
                """)).isEmpty();
    }

    @Test
    void migratedSchemaRejectsNullNonpositiveAndDuplicateNumbers() throws SQLException {
        insertLegacyVersion("rv_1", "cl_1", "v0.1", null);
        migrate();

        assertRejectedNumber(null, "23502");
        assertRejectedNumber(0L, "23513");
        assertRejectedNumber(-1L, "23513");
        assertRejectedNumber(1L, "23505");
        execute("INSERT INTO review_versions (id, cover_letter_id, version_number) VALUES ('rv_other', 'cl_2', 1)");
        execute("INSERT INTO review_versions (id, cover_letter_id, version_number) VALUES ('rv_max', 'cl_1', 9223372036854775807)");
        assertThat(rows("SELECT COUNT(*) FROM review_versions")).containsExactly(List.of(3L));
        assertThatThrownBy(() -> execute("DELETE FROM cover_letters WHERE id = 'cl_1'"))
                .isInstanceOf(SQLException.class);
    }

    private void migrate() {
        ScriptUtils.executeSqlScript(connection,
                new FileSystemResource("scripts/migrations/review-version-number/h2.sql"));
    }

    private void insertLegacyVersion(String id, String coverLetterId, String version, String jobId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO review_versions (id, cover_letter_id, version, llm_job_id, request_instruction)
                VALUES (?, ?, ?, ?, '기존 요구사항')
                """)) {
            statement.setString(1, id);
            statement.setString(2, coverLetterId);
            statement.setString(3, version);
            statement.setString(4, jobId);
            statement.executeUpdate();
        }
    }

    private void assertRejectedNumber(Long number, String sqlState) {
        assertThatThrownBy(() -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO review_versions (id, cover_letter_id, version_number)
                    VALUES ('rv_rejected', 'cl_1', ?)
                    """)) {
                statement.setObject(1, number);
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(((SQLException) exception).getSQLState()).isEqualTo(sqlState));
    }

    private void assertFinalSchema() throws SQLException {
        assertThat(rows("""
                SELECT LOWER(data_type), is_nullable FROM information_schema.columns
                WHERE table_name = 'review_versions' AND column_name = 'version_number'
                """)).containsExactly(List.of("bigint", "NO"));
        assertThat(rows("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'review_versions' AND column_name = 'version'
                """)).isEmpty();
        assertThat(rows("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_name = 'review_versions' AND constraint_name IN (
                    'ck_review_versions_version_number_positive',
                    'uk_review_versions_cover_letter_version_number'
                ) ORDER BY constraint_name
                """)).containsExactly(
                List.of("ck_review_versions_version_number_positive"),
                List.of("uk_review_versions_cover_letter_version_number")
        );
        assertThat(rows("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_name = 'review_versions' AND constraint_name = 'uk_review_versions_cover_letter_version'
                """)).isEmpty();
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<List<Object>> rows(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            List<List<Object>> rows = new ArrayList<>();
            while (result.next()) {
                List<Object> row = new ArrayList<>();
                for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                    row.add(result.getObject(column));
                }
                rows.add(row);
            }
            return rows;
        }
    }
}
