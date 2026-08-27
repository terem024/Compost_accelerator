package com.group11.compostsystem.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Opt-in against a disposable MySQL server; no application tables or credentials are used.
@EnabledIfEnvironmentVariable(named = "COMPOST_TEST_MYSQL_URL", matches = ".+")
class SensorTransportMysqlTests {
    private Connection admin;
    private String schema;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        admin = connect("UTC", true);
        schema = "sensor_transport_test_" + UUID.randomUUID().toString().replace("-", "");
        execute(admin, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        admin.setCatalog(schema);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (admin != null) {
            try {
                if (schema != null) execute(admin, "DROP DATABASE " + schema);
            } finally {
                admin.close();
            }
        }
    }

    @Test
    void mismatchedConnectionTimezoneMakesFreshReadingEightHoursOld() throws Exception {
        try (Connection connection = connect("Asia/Manila", false)) {
            connection.setCatalog(schema);
            execute(connection, "SET time_zone = '+00:00'");
            createReading(connection);
            long age = Duration.between(latestReading(connection).toInstant(), Instant.now()).toSeconds();
            assertTrue(Math.abs(age - 28800) < 5, "Expected the reported eight-hour shift, got " + age);
        }
    }

    @Test
    void alignedUtcKeepsFreshReadingConnectedAndCooldownInTheFuture() throws Exception {
        createReading(admin);
        Timestamp readingAt = latestReading(admin);
        assertTrue(Math.abs(Duration.between(readingAt.toInstant(), Instant.now()).toSeconds()) < 5);
        execute(admin, "CREATE TABLE sensor_connection_logs (log_id BIGINT, event_type VARCHAR(20), "
                + "sensor_status VARCHAR(10), last_reading_at DATETIME, occurred_at DATETIME)");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(admin, true));
        var service = new SensorConnectionService(jdbc, new SensorSseService(), 180);
        service.initialize();
        assertEquals("CONNECTED", service.getStatus().getConnectionStatus());

        execute(admin, "CREATE TABLE cooldown_test (cooldown_until DATETIME)");
        Timestamp until = Timestamp.from(Instant.now().plusSeconds(45));
        try (var insert = admin.prepareStatement("INSERT INTO cooldown_test VALUES (?)")) {
            insert.setTimestamp(1, until);
            insert.executeUpdate();
        }
        try (var statement = admin.createStatement();
             var result = statement.executeQuery("SELECT cooldown_until, cooldown_until > NOW() AS cooling FROM cooldown_test")) {
            assertTrue(result.next());
            assertTrue(result.getBoolean("cooling"));
            assertTrue(Math.abs(Duration.between(until.toInstant(), result.getTimestamp(1).toInstant()).toSeconds()) <= 1);
        }
    }

    @Test
    void runtimeRepairPreventsFailureAfterCooldownWasAlreadyWritten() throws Exception {
        execute(admin, "CREATE TABLE actuator_runtime_status ("
                + "actuator_type ENUM('FAN','WATER_SPRAY') PRIMARY KEY, current_status ENUM('ON','OFF'), "
                + "last_activated_at DATETIME, cooldown_until DATETIME, last_duration_seconds INT, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        String script = Files.readString(Path.of("../database/repair_actuator_runtime_collation.sql"));
        int start = script.indexOf("CREATE PROCEDURE");
        String repaired = script.substring(start, script.indexOf("END$$", start) + 3);
        String original = repaired.replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci", "");
        execute(admin, original);
        SQLException failure = assertThrows(SQLException.class, () -> pulse("WATER_SPRAY"));
        assertEquals(1267, failure.getErrorCode());
        assertEquals(1, coolingCount()); // Autocommit persisted the cooldown despite the failed CALL.

        for (int pass = 0; pass < 2; pass++) {
            execute(admin, "DROP PROCEDURE sp_update_actuator_runtime_status");
            execute(admin, repaired);
            pulse("WATER_SPRAY");
            pulse("FAN");
            assertEquals(2, coolingCount());
        }
    }

    @Test
    void sessionRepairAllowsRefreshButRejectsExpiredRevokedAndUnknownTokens() throws Exception {
        execute(admin, "CREATE TABLE users (user_id INT PRIMARY KEY, full_name VARCHAR(100), username VARCHAR(50), role VARCHAR(20))");
        execute(admin, "INSERT INTO users VALUES (1,'Test User','test@example.com','OPERATOR')");
        execute(admin, "CREATE TABLE user_sessions (session_id INT PRIMARY KEY, user_id INT, "
                + "session_token_hash CHAR(64), expires_at DATETIME, last_seen_at DATETIME, "
                + "status VARCHAR(20), revoked_at DATETIME) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        execute(admin, "INSERT INTO user_sessions VALUES "
                + "(1,1,'valid',DATE_ADD(NOW(),INTERVAL 1 HOUR),NOW(),'ACTIVE',NULL),"
                + "(2,1,'expired',DATE_SUB(NOW(),INTERVAL 1 HOUR),NOW(),'ACTIVE',NULL),"
                + "(3,1,'revoked',DATE_ADD(NOW(),INTERVAL 1 HOUR),NOW(),'REVOKED',NOW())");
        String script = Files.readString(Path.of("../database/repair_session_collation.sql"));
        int first = script.indexOf("CREATE PROCEDURE");
        String validate = script.substring(first, script.indexOf("END$$", first) + 3);
        execute(admin, validate.replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci", ""));
        assertEquals(1267, assertThrows(SQLException.class,
                () -> execute(admin, "CALL sp_validate_user_session('valid')")).getErrorCode());
        for (int pass = 0; pass < 2; pass++) {
            for (String name : new String[]{"sp_validate_user_session", "sp_refresh_user_session", "sp_logout_user_session"}) {
                execute(admin, "DROP PROCEDURE IF EXISTS " + name);
                int start = script.indexOf("CREATE PROCEDURE `" + name + "`");
                execute(admin, script.substring(start, script.indexOf("END$$", start) + 3));
            }
            assertEquals(1, sessionRows("CALL sp_validate_user_session('valid')"));
            assertEquals(1, sessionRows("CALL sp_refresh_user_session('valid',DATE_ADD(NOW(),INTERVAL 1 HOUR))"));
            for (String token : new String[]{"expired", "revoked", "unknown"}) {
                assertEquals(0, sessionRows("CALL sp_validate_user_session('" + token + "')"));
                assertEquals(0, sessionRows("CALL sp_refresh_user_session('" + token + "',DATE_ADD(NOW(),INTERVAL 1 HOUR))"));
            }
        }
        execute(admin, "CALL sp_logout_user_session('valid')");
        assertEquals(0, sessionRows("CALL sp_validate_user_session('valid')"));
    }

    private int sessionRows(String sql) throws SQLException {
        try (var statement = admin.createStatement()) {
            boolean result = statement.execute(sql);
            while (!result && statement.getUpdateCount() != -1) result = statement.getMoreResults();
            if (!result) return 0;
            try (var rows = statement.getResultSet()) {
                int count = 0;
                while (rows.next()) count++;
                return count;
            }
        }
    }

    private Connection connect(String zone, boolean force) throws SQLException {
        String base = System.getenv("COMPOST_TEST_MYSQL_URL");
        String url = base + (base.contains("?") ? "&" : "?") + "connectionTimeZone=" + zone
                + "&forceConnectionTimeZoneToSession=" + force + "&preserveInstants=true";
        return DriverManager.getConnection(url, "root", "");
    }

    private void pulse(String type) throws SQLException {
        execute(admin, "CALL sp_update_actuator_runtime_status('" + type
                + "','ON',NOW(),DATE_ADD(NOW(),INTERVAL 45 SECOND),15)");
    }

    private int coolingCount() throws SQLException {
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(
                "SELECT COUNT(*) FROM actuator_runtime_status WHERE cooldown_until > NOW()")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void createReading(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE sensor_readings (created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        execute(connection, "INSERT INTO sensor_readings () VALUES ()");
    }

    private Timestamp latestReading(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT MAX(created_at) FROM sensor_readings")) {
            result.next();
            return result.getTimestamp(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
