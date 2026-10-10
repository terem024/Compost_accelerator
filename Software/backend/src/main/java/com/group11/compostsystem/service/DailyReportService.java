package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.DailyReportResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class DailyReportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyReportService.class);
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Manila");

    private final JdbcTemplate jdbcTemplate;

    public DailyReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureDailyReportTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS daily_sensor_reports (
                    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_id INT NULL,
                    report_date DATE NOT NULL,
                    reading_count BIGINT NOT NULL DEFAULT 0,
                    average_moisture DECIMAL(10,2) NULL,
                    minimum_moisture DECIMAL(10,2) NULL,
                    maximum_moisture DECIMAL(10,2) NULL,
                    average_gas DECIMAL(10,2) NULL,
                    minimum_gas DECIMAL(10,2) NULL,
                    maximum_gas DECIMAL(10,2) NULL,
                    average_temperature DECIMAL(10,2) NULL,
                    minimum_temperature DECIMAL(10,2) NULL,
                    maximum_temperature DECIMAL(10,2) NULL,
                    average_humidity DECIMAL(10,2) NULL,
                    minimum_humidity DECIMAL(10,2) NULL,
                    maximum_humidity DECIMAL(10,2) NULL,
                    sensor_availability_issues TEXT NULL,
                    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_daily_sensor_reports_batch_date (batch_id, report_date)
                )
                """);

        if (!schemaObjectExists("COLUMNS", "batch_id")) {
            jdbcTemplate.execute("ALTER TABLE daily_sensor_reports ADD COLUMN batch_id INT NULL AFTER report_id");
        }
        if (schemaObjectExists("STATISTICS", "uq_daily_sensor_reports_report_date")) {
            jdbcTemplate.execute("ALTER TABLE daily_sensor_reports DROP INDEX uq_daily_sensor_reports_report_date");
        }
        if (!schemaObjectExists("STATISTICS", "uq_daily_sensor_reports_batch_date")) {
            jdbcTemplate.execute("ALTER TABLE daily_sensor_reports ADD UNIQUE KEY uq_daily_sensor_reports_batch_date (batch_id, report_date)");
        }

        migrateLegacyReports();
    }

    private boolean schemaObjectExists(String table, String objectName) {
        String metadataTable = "COLUMNS".equals(table) ? "COLUMNS" : "STATISTICS";
        String objectColumn = "COLUMNS".equals(table) ? "COLUMN_NAME" : "INDEX_NAME";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema." + metadataTable
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_sensor_reports' AND "
                        + objectColumn + " = ?",
                Integer.class,
                objectName
        );
        return count != null && count > 0;
    }

    /** Rebuild old date-only summaries by batch from their retained source readings. */
    private void migrateLegacyReports() {
        List<LocalDate> legacyDates = jdbcTemplate.query(
                "SELECT report_date FROM daily_sensor_reports WHERE batch_id IS NULL ORDER BY report_date",
                (rs, rowNum) -> rs.getDate("report_date").toLocalDate()
        );

        for (LocalDate reportDate : legacyDates) {
            List<Integer> batchIds = getBatchIdsWithReadings(reportDate);
            if (batchIds.isEmpty()) continue;

            for (Integer batchId : batchIds) {
                saveReportForBatch(reportDate, batchId);
            }
            jdbcTemplate.update(
                    "DELETE FROM daily_sensor_reports WHERE batch_id IS NULL AND report_date = ?",
                    reportDate
            );
        }
    }

    @Scheduled(cron = "${app.daily-report.cron:0 0 6 * * *}", zone = "${app.daily-report.zone:Asia/Manila}")
    public void generateYesterdayReportOnSchedule() {
        LocalDate reportDate = LocalDate.now(REPORT_ZONE).minusDays(1);
        try {
            List<Integer> batchIds = getBatchIdsWithReadings(reportDate);
            for (Integer batchId : batchIds) {
                saveReportForBatch(reportDate, batchId);
            }
            LOGGER.info("Generated {} daily sensor reports for {}.", batchIds.size(), reportDate);
        } catch (Exception exception) {
            LOGGER.warn("Daily sensor report generation failed for {}.", reportDate, exception);
        }
    }

    public DailyReportResponse generateYesterdayReport() {
        LocalDate reportDate = LocalDate.now(REPORT_ZONE).minusDays(1);
        List<Integer> batchIds = getBatchIdsWithReadings(reportDate);
        if (batchIds.isEmpty()) return null;
        return saveReportForBatch(reportDate, batchIds.get(0));
    }

    public List<DailyReportResponse> getRecentReports() {
        return jdbcTemplate.query(reportSelect() + """
                ORDER BY COALESCE(cb.start_date, dsr.report_date) DESC,
                         dsr.batch_id DESC, dsr.report_date DESC
                """, (rs, rowNum) -> mapReport(rs));
    }

    private String reportSelect() {
        return """
                SELECT dsr.report_id, dsr.batch_id, cb.batch_code, cb.batch_name,
                       cb.status AS batch_status, cb.start_date AS batch_start_date,
                       dsr.report_date, dsr.reading_count,
                       dsr.average_moisture, dsr.minimum_moisture, dsr.maximum_moisture,
                       dsr.average_gas, dsr.minimum_gas, dsr.maximum_gas,
                       dsr.average_temperature, dsr.minimum_temperature, dsr.maximum_temperature,
                       dsr.average_humidity, dsr.minimum_humidity, dsr.maximum_humidity,
                       dsr.sensor_availability_issues, dsr.generated_at
                FROM daily_sensor_reports dsr
                LEFT JOIN compost_batches cb ON cb.batch_id = dsr.batch_id
                """;
    }

    private List<Integer> getBatchIdsWithReadings(LocalDate reportDate) {
        Timestamp start = Timestamp.valueOf(reportDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(reportDate.plusDays(1).atStartOfDay());
        return jdbcTemplate.query(
                """
                SELECT DISTINCT sr.batch_id
                FROM sensor_readings sr
                JOIN compost_batches cb ON cb.batch_id = sr.batch_id
                WHERE sr.batch_id IS NOT NULL
                  AND sr.created_at >= ? AND sr.created_at < ?
                  AND cb.start_date < ?
                ORDER BY sr.batch_id
                """,
                (rs, rowNum) -> rs.getInt("batch_id"),
                start, end, reportDate
        );
    }

    private DailyReportResponse saveReportForBatch(LocalDate reportDate, Integer batchId) {
        ReportSummary summary = summarizeReadings(reportDate, batchId);
        String issues = buildAvailabilityIssues(summary);

        jdbcTemplate.update("""
                INSERT INTO daily_sensor_reports (
                    batch_id, report_date, reading_count,
                    average_moisture, minimum_moisture, maximum_moisture,
                    average_gas, minimum_gas, maximum_gas,
                    average_temperature, minimum_temperature, maximum_temperature,
                    average_humidity, minimum_humidity, maximum_humidity,
                    sensor_availability_issues, generated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    reading_count = VALUES(reading_count),
                    average_moisture = VALUES(average_moisture),
                    minimum_moisture = VALUES(minimum_moisture),
                    maximum_moisture = VALUES(maximum_moisture),
                    average_gas = VALUES(average_gas),
                    minimum_gas = VALUES(minimum_gas),
                    maximum_gas = VALUES(maximum_gas),
                    average_temperature = VALUES(average_temperature),
                    minimum_temperature = VALUES(minimum_temperature),
                    maximum_temperature = VALUES(maximum_temperature),
                    average_humidity = VALUES(average_humidity),
                    minimum_humidity = VALUES(minimum_humidity),
                    maximum_humidity = VALUES(maximum_humidity),
                    sensor_availability_issues = VALUES(sensor_availability_issues),
                    generated_at = CURRENT_TIMESTAMP
                """,
                batchId, reportDate, summary.readingCount(),
                summary.averageMoisture(), summary.minimumMoisture(), summary.maximumMoisture(),
                summary.averageGas(), summary.minimumGas(), summary.maximumGas(),
                summary.averageTemperature(), summary.minimumTemperature(), summary.maximumTemperature(),
                summary.averageHumidity(), summary.minimumHumidity(), summary.maximumHumidity(), issues
        );

        return getReportByDateAndBatch(reportDate, batchId);
    }

    private DailyReportResponse getReportByDateAndBatch(LocalDate reportDate, Integer batchId) {
        return jdbcTemplate.queryForObject(
                reportSelect() + " WHERE dsr.report_date = ? AND dsr.batch_id = ?",
                (rs, rowNum) -> mapReport(rs), reportDate, batchId
        );
    }

    private ReportSummary summarizeReadings(LocalDate reportDate, Integer batchId) {
        Timestamp start = Timestamp.valueOf(reportDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(reportDate.plusDays(1).atStartOfDay());

        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS reading_count,
                    ROUND(AVG(moisture_level), 2) AS average_moisture,
                    MIN(moisture_level) AS minimum_moisture,
                    MAX(moisture_level) AS maximum_moisture,
                    ROUND(AVG(gas_level), 2) AS average_gas,
                    MIN(gas_level) AS minimum_gas,
                    MAX(gas_level) AS maximum_gas,
                    ROUND(AVG(temperature_c), 2) AS average_temperature,
                    MIN(temperature_c) AS minimum_temperature,
                    MAX(temperature_c) AS maximum_temperature,
                    ROUND(AVG(humidity_level), 2) AS average_humidity,
                    MIN(humidity_level) AS minimum_humidity,
                    MAX(humidity_level) AS maximum_humidity,
                    SUM(CASE WHEN moisture_level IS NULL THEN 1 ELSE 0 END) AS missing_moisture,
                    SUM(CASE WHEN gas_level IS NULL THEN 1 ELSE 0 END) AS missing_gas,
                    SUM(CASE WHEN temperature_c IS NULL THEN 1 ELSE 0 END) AS missing_temperature,
                    SUM(CASE WHEN humidity_level IS NULL THEN 1 ELSE 0 END) AS missing_humidity
                FROM sensor_readings
                WHERE created_at >= ? AND created_at < ? AND batch_id = ?
                """, (rs, rowNum) -> new ReportSummary(
                rs.getLong("reading_count"),
                rs.getBigDecimal("average_moisture"), rs.getBigDecimal("minimum_moisture"), rs.getBigDecimal("maximum_moisture"),
                rs.getBigDecimal("average_gas"), rs.getBigDecimal("minimum_gas"), rs.getBigDecimal("maximum_gas"),
                rs.getBigDecimal("average_temperature"), rs.getBigDecimal("minimum_temperature"), rs.getBigDecimal("maximum_temperature"),
                rs.getBigDecimal("average_humidity"), rs.getBigDecimal("minimum_humidity"), rs.getBigDecimal("maximum_humidity"),
                rs.getLong("missing_moisture"), rs.getLong("missing_gas"),
                rs.getLong("missing_temperature"), rs.getLong("missing_humidity")
        ), start, end, batchId);
    }

    private String buildAvailabilityIssues(ReportSummary summary) {
        if (summary.readingCount() == 0) return "No sensor readings were received for this date.";
        List<String> issues = new ArrayList<>();
        addMissingIssue(issues, "Moisture", summary.missingMoisture(), summary.readingCount());
        addMissingIssue(issues, "Gas", summary.missingGas(), summary.readingCount());
        addMissingIssue(issues, "Temperature", summary.missingTemperature(), summary.readingCount());
        addMissingIssue(issues, "Humidity", summary.missingHumidity(), summary.readingCount());
        return issues.isEmpty() ? "No sensor availability issues detected." : String.join(" ", issues);
    }

    private void addMissingIssue(List<String> issues, String name, long missing, long total) {
        if (missing > 0) issues.add("%s missing in %d of %d readings.".formatted(name, missing, total));
    }

    private DailyReportResponse mapReport(ResultSet rs) throws java.sql.SQLException {
        java.sql.Date batchStart = rs.getDate("batch_start_date");
        return new DailyReportResponse(
                rs.getLong("report_id"), rs.getObject("batch_id", Integer.class),
                rs.getString("batch_code"), rs.getString("batch_name"), rs.getString("batch_status"),
                batchStart == null ? null : batchStart.toLocalDate(),
                rs.getDate("report_date").toLocalDate(), rs.getLong("reading_count"),
                rs.getBigDecimal("average_moisture"), rs.getBigDecimal("minimum_moisture"), rs.getBigDecimal("maximum_moisture"),
                rs.getBigDecimal("average_gas"), rs.getBigDecimal("minimum_gas"), rs.getBigDecimal("maximum_gas"),
                rs.getBigDecimal("average_temperature"), rs.getBigDecimal("minimum_temperature"), rs.getBigDecimal("maximum_temperature"),
                rs.getBigDecimal("average_humidity"), rs.getBigDecimal("minimum_humidity"), rs.getBigDecimal("maximum_humidity"),
                rs.getString("sensor_availability_issues"), rs.getTimestamp("generated_at")
        );
    }

    private record ReportSummary(
            long readingCount,
            BigDecimal averageMoisture, BigDecimal minimumMoisture, BigDecimal maximumMoisture,
            BigDecimal averageGas, BigDecimal minimumGas, BigDecimal maximumGas,
            BigDecimal averageTemperature, BigDecimal minimumTemperature, BigDecimal maximumTemperature,
            BigDecimal averageHumidity, BigDecimal minimumHumidity, BigDecimal maximumHumidity,
            long missingMoisture, long missingGas, long missingTemperature, long missingHumidity
    ) { }
}
