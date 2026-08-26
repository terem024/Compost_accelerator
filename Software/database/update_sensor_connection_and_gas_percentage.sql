-- Adds durable ESP32 connection events and converts the MQ135 index (0-2000)
-- to a percentage (0-100). This migration is safe to run more than once.

USE `compost_system`;

CREATE TABLE IF NOT EXISTS `sensor_connection_logs` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` enum('DISCONNECTED','RECONNECTED') NOT NULL,
  `sensor_status` varchar(10) NOT NULL DEFAULT 'NA',
  `last_reading_at` datetime DEFAULT NULL,
  `occurred_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`log_id`),
  KEY `idx_sensor_connection_occurred_at` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

UPDATE `sensor_readings`
SET `gas_level` = ROUND(`gas_level` / 20.0, 2)
WHERE `gas_level` > 100.00;

UPDATE `threshold_settings`
SET `gas_max` = ROUND(`gas_max` / 20.0, 2)
WHERE `gas_max` > 100.00;

UPDATE `actuator_logs`
SET
  `trigger_value` = CASE
    WHEN `trigger_value` > 100.00 THEN ROUND(`trigger_value` / 20.0, 2)
    ELSE `trigger_value`
  END,
  `threshold_value` = CASE
    WHEN `threshold_value` > 100.00 THEN ROUND(`threshold_value` / 20.0, 2)
    ELSE `threshold_value`
  END
WHERE `trigger_source` = 'GAS';

UPDATE `sensor_readings`
SET `gas_status` = CASE
  WHEN `gas_level` < 40.00 THEN 'LOW'
  WHEN `gas_level` > COALESCE(
    (SELECT `gas_max` FROM `threshold_settings` ORDER BY `setting_id` DESC LIMIT 1),
    60.00
  ) THEN 'HIGH'
  ELSE 'NORMAL'
END;
