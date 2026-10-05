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
                    UNIQUE KEY uq_daily_sensor_reports_report_date (report_date)
                )
                """);
    }

    @Scheduled(cron = "${app.daily-report.cron:0 0 6 * * *}", zone = "${app.daily-report.zone:Asia/Manila}")
    public void generateYesterdayReportOnSchedule() {
        LocalDate reportDate = LocalDate.now(REPORT_ZONE).minusDays(1);
        try {
            generateReportForDate(reportDate);
            LOGGER.info("Daily sensor report generated for {}.", reportDate);
        } catch (Exception exception) {
            LOGGER.warn("Daily sensor report generation failed for {}.", reportDate, exception);
        }
    }

    public DailyReportResponse generateYesterdayReport() {
        return generateReportForDate(LocalDate.now(REPORT_ZONE).minusDays(1));
    }

    public DailyReportResponse generateReportForDate(LocalDate reportDate) {
        ReportSummary summary = summarizeReadings(reportDate);
        String issues = buildAvailabilityIssues(summary);

        jdbcTemplate.update("""
                INSERT INTO daily_sensor_reports (
                    report_date, reading_count,
                    average_moisture, minimum_moisture, maximum_moisture,
                    average_gas, minimum_gas, maximum_gas,
                    average_temperature, minimum_temperature, maximum_temperature,
                    average_humidity, minimum_humidity, maximum_humidity,
                    sensor_availability_issues, generated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
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
                reportDate,
                summary.readingCount(),
                summary.averageMoisture(), summary.minimumMoisture(), summary.maximumMoisture(),
                summary.averageGas(), summary.minimumGas(), summary.maximumGas(),
                summary.averageTemperature(), summary.minimumTemperature(), summary.maximumTemperature(),
                summary.averageHumidity(), summary.minimumHumidity(), summary.maximumHumidity(),
                issues
        );

        return getReportByDate(reportDate);
    }

    public List<DailyReportResponse> getRecentReports() {
        return jdbcTemplate.query("""
                SELECT report_id, report_date, reading_count,
                       average_moisture, minimum_moisture, maximum_moisture,
                       average_gas, minimum_gas, maximum_gas,
                       average_temperature, minimum_temperature, maximum_temperature,
                       average_humidity, minimum_humidity, maximum_humidity,
                       sensor_availability_issues, generated_at
                FROM daily_sensor_reports
                ORDER BY report_date DESC
                LIMIT 30
                """, (rs, rowNum) -> mapReport(rs));
    }

    private DailyReportResponse getReportByDate(LocalDate reportDate) {
        return jdbcTemplate.queryForObject("""
                SELECT report_id, report_date, reading_count,
                       average_moisture, minimum_moisture, maximum_moisture,
                       average_gas, minimum_gas, maximum_gas,
                       average_temperature, minimum_temperature, maximum_temperature,
                       average_humidity, minimum_humidity, maximum_humidity,
                       sensor_availability_issues, generated_at
                FROM daily_sensor_reports
                WHERE report_date = ?
                """, (rs, rowNum) -> mapReport(rs), reportDate);
    }

    private ReportSummary summarizeReadings(LocalDate reportDate) {
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
                WHERE created_at >= ? AND created_at < ?
                """, (rs, rowNum) -> new ReportSummary(
                rs.getLong("reading_count"),
                rs.getBigDecimal("average_moisture"),
                rs.getBigDecimal("minimum_moisture"),
                rs.getBigDecimal("maximum_moisture"),
                rs.getBigDecimal("average_gas"),
                rs.getBigDecimal("minimum_gas"),
                rs.getBigDecimal("maximum_gas"),
                rs.getBigDecimal("average_temperature"),
                rs.getBigDecimal("minimum_temperature"),
                rs.getBigDecimal("maximum_temperature"),
                rs.getBigDecimal("average_humidity"),
                rs.getBigDecimal("minimum_humidity"),
                rs.getBigDecimal("maximum_humidity"),
                rs.getLong("missing_moisture"),
                rs.getLong("missing_gas"),
                rs.getLong("missing_temperature"),
                rs.getLong("missing_humidity")
        ), start, end);
    }

    private String buildAvailabilityIssues(ReportSummary summary) {
        if (summary.readingCount() == 0) {
            return "No sensor readings were received for this date.";
        }

        List<String> issues = new ArrayList<>();
        addMissingIssue(issues, "Moisture", summary.missingMoisture(), summary.readingCount());
        addMissingIssue(issues, "Gas", summary.missingGas(), summary.readingCount());
        addMissingIssue(issues, "Temperature", summary.missingTemperature(), summary.readingCount());
        addMissingIssue(issues, "Humidity", summary.missingHumidity(), summary.readingCount());
        return issues.isEmpty() ? "No sensor availability issues detected." : String.join(" ", issues);
    }

    private void addMissingIssue(List<String> issues, String sensorName, long missingCount, long readingCount) {
        if (missingCount > 0) {
            issues.add("%s missing in %d of %d readings.".formatted(sensorName, missingCount, readingCount));
        }
    }

    private DailyReportResponse mapReport(ResultSet rs) throws java.sql.SQLException {
        return new DailyReportResponse(
                rs.getLong("report_id"),
                rs.getDate("report_date").toLocalDate(),
                rs.getLong("reading_count"),
                rs.getBigDecimal("average_moisture"),
                rs.getBigDecimal("minimum_moisture"),
                rs.getBigDecimal("maximum_moisture"),
                rs.getBigDecimal("average_gas"),
                rs.getBigDecimal("minimum_gas"),
                rs.getBigDecimal("maximum_gas"),
                rs.getBigDecimal("average_temperature"),
                rs.getBigDecimal("minimum_temperature"),
                rs.getBigDecimal("maximum_temperature"),
                rs.getBigDecimal("average_humidity"),
                rs.getBigDecimal("minimum_humidity"),
                rs.getBigDecimal("maximum_humidity"),
                rs.getString("sensor_availability_issues"),
                rs.getTimestamp("generated_at")
        );
    }

    private record ReportSummary(
            long readingCount,
            BigDecimal averageMoisture,
            BigDecimal minimumMoisture,
            BigDecimal maximumMoisture,
            BigDecimal averageGas,
            BigDecimal minimumGas,
            BigDecimal maximumGas,
            BigDecimal averageTemperature,
            BigDecimal minimumTemperature,
            BigDecimal maximumTemperature,
            BigDecimal averageHumidity,
            BigDecimal minimumHumidity,
            BigDecimal maximumHumidity,
            long missingMoisture,
            long missingGas,
            long missingTemperature,
            long missingHumidity
    ) {
    }
}
