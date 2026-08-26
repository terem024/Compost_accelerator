-- Sensor and actuator foundation for pulse-based compost control.
-- Apply to the existing compost_system database.

ALTER TABLE `threshold_settings`
  ADD COLUMN IF NOT EXISTS `reading_interval_seconds` int(11) NOT NULL DEFAULT 30 AFTER `gas_max`,
  ADD COLUMN IF NOT EXISTS `spray_duration_seconds` int(11) NOT NULL DEFAULT 5 AFTER `reading_interval_seconds`,
  ADD COLUMN IF NOT EXISTS `fan_duration_seconds` int(11) NOT NULL DEFAULT 5 AFTER `spray_duration_seconds`,
  ADD COLUMN IF NOT EXISTS `spray_cooldown_seconds` int(11) NOT NULL DEFAULT 30 AFTER `fan_duration_seconds`,
  ADD COLUMN IF NOT EXISTS `fan_cooldown_seconds` int(11) NOT NULL DEFAULT 30 AFTER `spray_cooldown_seconds`;

ALTER TABLE `threshold_settings`
  MODIFY `reading_interval_seconds` int(11) NOT NULL DEFAULT 30,
  MODIFY `spray_duration_seconds` int(11) NOT NULL DEFAULT 5,
  MODIFY `fan_duration_seconds` int(11) NOT NULL DEFAULT 5,
  MODIFY `spray_cooldown_seconds` int(11) NOT NULL DEFAULT 30,
  MODIFY `fan_cooldown_seconds` int(11) NOT NULL DEFAULT 30;

UPDATE `threshold_settings`
SET
  `moisture_min` = 50.00,
  `gas_max` = 60.00,
  `reading_interval_seconds` = COALESCE(`reading_interval_seconds`, 30),
  `spray_duration_seconds` = COALESCE(`spray_duration_seconds`, 5),
  `fan_duration_seconds` = COALESCE(`fan_duration_seconds`, 5),
  `spray_cooldown_seconds` = COALESCE(`spray_cooldown_seconds`, 30),
  `fan_cooldown_seconds` = COALESCE(`fan_cooldown_seconds`, 30)
WHERE `setting_id` = (
  SELECT latest_setting_id
  FROM (
    SELECT MAX(`setting_id`) AS latest_setting_id
    FROM `threshold_settings`
  ) latest
);

INSERT INTO `threshold_settings`
  (
    `moisture_min`,
    `gas_max`,
    `reading_interval_seconds`,
    `spray_duration_seconds`,
    `fan_duration_seconds`,
    `spray_cooldown_seconds`,
    `fan_cooldown_seconds`,
    `updated_by`
  )
SELECT 50.00, 60.00, 30, 5, 5, 30, 30, NULL
WHERE NOT EXISTS (SELECT 1 FROM `threshold_settings`);

ALTER TABLE `actuator_logs`
  MODIFY `trigger_source` enum('MOISTURE','GAS','AUTO','MANUAL','SAFETY') NOT NULL DEFAULT 'AUTO';

ALTER TABLE `actuator_logs`
  ADD COLUMN IF NOT EXISTS `reading_id` int(11) DEFAULT NULL AFTER `batch_id`,
  ADD COLUMN IF NOT EXISTS `trigger_value` decimal(10,2) DEFAULT NULL AFTER `trigger_source`,
  ADD COLUMN IF NOT EXISTS `threshold_value` decimal(10,2) DEFAULT NULL AFTER `trigger_value`,
  ADD COLUMN IF NOT EXISTS `duration_seconds` int(11) DEFAULT NULL AFTER `threshold_value`,
  ADD COLUMN IF NOT EXISTS `started_at` datetime DEFAULT NULL AFTER `duration_seconds`,
  ADD COLUMN IF NOT EXISTS `ended_at` datetime DEFAULT NULL AFTER `started_at`;

UPDATE `actuator_logs`
SET
  `reading_id` = COALESCE(`reading_id`, `related_reading_id`),
  `started_at` = COALESCE(`started_at`, `created_at`),
  `ended_at` = COALESCE(`ended_at`, DATE_ADD(`created_at`, INTERVAL 5 SECOND)),
  `duration_seconds` = COALESCE(`duration_seconds`, 5)
WHERE `reading_id` IS NULL
   OR `started_at` IS NULL
   OR `ended_at` IS NULL
   OR `duration_seconds` IS NULL;

CREATE TABLE IF NOT EXISTS `actuator_runtime_status` (
  `actuator_type` enum('FAN','WATER_SPRAY') NOT NULL,
  `current_status` enum('ON','OFF') NOT NULL DEFAULT 'OFF',
  `last_activated_at` datetime DEFAULT NULL,
  `cooldown_until` datetime DEFAULT NULL,
  `last_duration_seconds` int(11) NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`actuator_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `actuator_runtime_status`
  (`actuator_type`, `current_status`, `last_duration_seconds`)
VALUES
  ('FAN', 'OFF', 0),
  ('WATER_SPRAY', 'OFF', 0)
ON DUPLICATE KEY UPDATE
  `actuator_type` = VALUES(`actuator_type`);

DROP PROCEDURE IF EXISTS `sp_get_threshold_settings`;
DROP PROCEDURE IF EXISTS `sp_save_threshold_settings`;
DROP PROCEDURE IF EXISTS `sp_save_sensor_reading`;
DROP PROCEDURE IF EXISTS `sp_get_latest_sensor_reading`;
DROP PROCEDURE IF EXISTS `sp_get_sensor_reading_history`;
DROP PROCEDURE IF EXISTS `sp_insert_actuator_log`;
DROP PROCEDURE IF EXISTS `sp_get_actuator_runtime_status`;
DROP PROCEDURE IF EXISTS `sp_update_actuator_runtime_status`;
DROP PROCEDURE IF EXISTS `sp_get_latest_actuator_status`;
DROP PROCEDURE IF EXISTS `sp_get_actuator_log_history`;

DELIMITER $$

CREATE PROCEDURE `sp_get_threshold_settings` ()
BEGIN
  IF EXISTS (SELECT 1 FROM `threshold_settings`) THEN
    SELECT
      `moisture_min`,
      `gas_max`,
      `reading_interval_seconds`,
      `spray_duration_seconds`,
      `fan_duration_seconds`,
      `spray_cooldown_seconds`,
      `fan_cooldown_seconds`,
      `updated_by`,
      `updated_at`
    FROM `threshold_settings`
    ORDER BY `setting_id` DESC
    LIMIT 1;
  ELSE
    SELECT
      CAST(50.00 AS DECIMAL(5,2)) AS `moisture_min`,
      CAST(60.00 AS DECIMAL(8,2)) AS `gas_max`,
      30 AS `reading_interval_seconds`,
      5 AS `spray_duration_seconds`,
      5 AS `fan_duration_seconds`,
      30 AS `spray_cooldown_seconds`,
      30 AS `fan_cooldown_seconds`,
      NULL AS `updated_by`,
      NULL AS `updated_at`;
  END IF;
END$$

CREATE PROCEDURE `sp_save_threshold_settings` (
  IN `p_moisture_min` DECIMAL(5,2),
  IN `p_gas_max` DECIMAL(8,2),
  IN `p_reading_interval_seconds` INT,
  IN `p_spray_duration_seconds` INT,
  IN `p_fan_duration_seconds` INT,
  IN `p_spray_cooldown_seconds` INT,
  IN `p_fan_cooldown_seconds` INT,
  IN `p_updated_by` INT
)
BEGIN
  INSERT INTO `threshold_settings`
    (
      `moisture_min`,
      `gas_max`,
      `reading_interval_seconds`,
      `spray_duration_seconds`,
      `fan_duration_seconds`,
      `spray_cooldown_seconds`,
      `fan_cooldown_seconds`,
      `updated_by`
    )
  VALUES
    (
      p_moisture_min,
      p_gas_max,
      p_reading_interval_seconds,
      p_spray_duration_seconds,
      p_fan_duration_seconds,
      p_spray_cooldown_seconds,
      p_fan_cooldown_seconds,
      p_updated_by
    );

  CALL `sp_get_threshold_settings`();
END$$

CREATE PROCEDURE `sp_save_sensor_reading` (
  IN `p_batch_id` INT,
  IN `p_moisture_level` DECIMAL(5,2),
  IN `p_gas_level` DECIMAL(8,2),
  IN `p_temperature_c` DECIMAL(5,2),
  IN `p_humidity_level` DECIMAL(5,2)
)
BEGIN
  DECLARE `v_batch_id` INT DEFAULT NULL;
  DECLARE `v_moisture_min` DECIMAL(5,2) DEFAULT 50.00;
  DECLARE `v_gas_max` DECIMAL(8,2) DEFAULT 60.00;
  DECLARE `v_moisture_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_gas_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_temperature_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_humidity_status` VARCHAR(10) DEFAULT 'NORMAL';

  SET `v_batch_id` = p_batch_id;

  IF `v_batch_id` IS NULL THEN
    SELECT `batch_id`
    INTO `v_batch_id`
    FROM `compost_batches`
    WHERE `status` = 'ACTIVE'
    ORDER BY `start_date` DESC, `batch_id` DESC
    LIMIT 1;
  END IF;

  IF `v_batch_id` IS NULL THEN
    SET `v_batch_id` = 1;
  END IF;

  IF EXISTS (SELECT 1 FROM `threshold_settings`) THEN
    SELECT `moisture_min`, `gas_max`
    INTO `v_moisture_min`, `v_gas_max`
    FROM `threshold_settings`
    ORDER BY `setting_id` DESC
    LIMIT 1;
  END IF;

  SET `v_moisture_status` = CASE
    WHEN p_moisture_level < `v_moisture_min` THEN 'LOW'
    WHEN p_moisture_level > 70.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_gas_status` = CASE
    WHEN p_gas_level < 40.00 THEN 'LOW'
    WHEN p_gas_level > `v_gas_max` THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_temperature_status` = CASE
    WHEN p_temperature_c < 30.00 THEN 'LOW'
    WHEN p_temperature_c > 50.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_humidity_status` = CASE
    WHEN p_humidity_level < 40.00 THEN 'LOW'
    WHEN p_humidity_level > 70.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  INSERT INTO `sensor_readings`
    (
      `batch_id`,
      `moisture_level`,
      `gas_level`,
      `temperature_c`,
      `humidity_level`,
      `moisture_status`,
      `gas_status`,
      `temperature_status`,
      `humidity_status`
    )
  VALUES
    (
      `v_batch_id`,
      p_moisture_level,
      p_gas_level,
      p_temperature_c,
      p_humidity_level,
      `v_moisture_status`,
      `v_gas_status`,
      `v_temperature_status`,
      `v_humidity_status`
    );

  SELECT
    `reading_id`,
    `batch_id`,
    `moisture_level`,
    `gas_level`,
    `temperature_c`,
    `humidity_level`,
    `moisture_status`,
    `gas_status`,
    `temperature_status`,
    `humidity_status`,
    `created_at`
  FROM `sensor_readings`
  WHERE `reading_id` = LAST_INSERT_ID();
END$$

CREATE PROCEDURE `sp_get_latest_sensor_reading` ()
BEGIN
  DECLARE `v_batch_id` INT DEFAULT NULL;

  SELECT `batch_id`
  INTO `v_batch_id`
  FROM `compost_batches`
  WHERE `status` = 'ACTIVE'
  ORDER BY `start_date` DESC, `batch_id` DESC
  LIMIT 1;

  IF `v_batch_id` IS NULL THEN
    SET `v_batch_id` = 1;
  END IF;

  IF EXISTS (SELECT 1 FROM `sensor_readings` WHERE `batch_id` = `v_batch_id`) THEN
    SELECT
      `reading_id`,
      `batch_id`,
      `moisture_level`,
      `gas_level`,
      `temperature_c`,
      `humidity_level`,
      `moisture_status`,
      `gas_status`,
      `temperature_status`,
      `humidity_status`,
      `created_at`
    FROM `sensor_readings`
    WHERE `batch_id` = `v_batch_id`
    ORDER BY `created_at` DESC, `reading_id` DESC
    LIMIT 1;
  ELSE
    SELECT
      `reading_id`,
      `batch_id`,
      `moisture_level`,
      `gas_level`,
      `temperature_c`,
      `humidity_level`,
      `moisture_status`,
      `gas_status`,
      `temperature_status`,
      `humidity_status`,
      `created_at`
    FROM `sensor_readings`
    ORDER BY `created_at` DESC, `reading_id` DESC
    LIMIT 1;
  END IF;
END$$

CREATE PROCEDURE `sp_get_sensor_reading_history` ()
BEGIN
  SELECT
    `reading_id`,
    `batch_id`,
    `moisture_level`,
    `gas_level`,
    `temperature_c`,
    `humidity_level`,
    `moisture_status`,
    `gas_status`,
    `temperature_status`,
    `humidity_status`,
    `created_at`
  FROM `sensor_readings`
  ORDER BY `created_at` DESC, `reading_id` DESC
  LIMIT 100;
END$$

CREATE PROCEDURE `sp_insert_actuator_log` (
  IN `p_batch_id` INT,
  IN `p_reading_id` INT,
  IN `p_actuator_type` VARCHAR(20),
  IN `p_status` VARCHAR(10),
  IN `p_trigger_source` VARCHAR(20),
  IN `p_trigger_value` DECIMAL(10,2),
  IN `p_threshold_value` DECIMAL(10,2),
  IN `p_duration_seconds` INT,
  IN `p_started_at` DATETIME,
  IN `p_ended_at` DATETIME
)
BEGIN
  INSERT INTO `actuator_logs`
    (
      `batch_id`,
      `reading_id`,
      `actuator_type`,
      `status`,
      `trigger_source`,
      `trigger_value`,
      `threshold_value`,
      `duration_seconds`,
      `started_at`,
      `ended_at`,
      `related_reading_id`,
      `reason`
    )
  VALUES
    (
      p_batch_id,
      p_reading_id,
      p_actuator_type,
      p_status,
      p_trigger_source,
      p_trigger_value,
      p_threshold_value,
      p_duration_seconds,
      p_started_at,
      p_ended_at,
      p_reading_id,
      CONCAT(p_trigger_source, ' threshold pulse activation')
    );

  SELECT
    `log_id`,
    `batch_id`,
    `reading_id`,
    `actuator_type`,
    `status`,
    `trigger_source`,
    `trigger_value`,
    `threshold_value`,
    `duration_seconds`,
    `started_at`,
    `ended_at`,
    `created_at`
  FROM `actuator_logs`
  WHERE `log_id` = LAST_INSERT_ID();
END$$

CREATE PROCEDURE `sp_get_actuator_runtime_status` ()
BEGIN
  UPDATE `actuator_runtime_status`
  SET `current_status` = 'OFF'
  WHERE `current_status` = 'ON'
    AND `last_activated_at` IS NOT NULL
    AND DATE_ADD(`last_activated_at`, INTERVAL `last_duration_seconds` SECOND) <= NOW();

  SELECT
    `actuator_type`,
    `current_status`,
    `last_activated_at`,
    `cooldown_until`,
    `last_duration_seconds`,
    `updated_at`
  FROM `actuator_runtime_status`
  ORDER BY FIELD(`actuator_type`, 'FAN', 'WATER_SPRAY');
END$$

CREATE PROCEDURE `sp_update_actuator_runtime_status` (
  IN `p_actuator_type` VARCHAR(20),
  IN `p_current_status` VARCHAR(10),
  IN `p_last_activated_at` DATETIME,
  IN `p_cooldown_until` DATETIME,
  IN `p_last_duration_seconds` INT
)
BEGIN
  INSERT INTO `actuator_runtime_status`
    (
      `actuator_type`,
      `current_status`,
      `last_activated_at`,
      `cooldown_until`,
      `last_duration_seconds`
    )
  VALUES
    (
      p_actuator_type,
      p_current_status,
      p_last_activated_at,
      p_cooldown_until,
      p_last_duration_seconds
    )
  ON DUPLICATE KEY UPDATE
    `current_status` = VALUES(`current_status`),
    `last_activated_at` = VALUES(`last_activated_at`),
    `cooldown_until` = VALUES(`cooldown_until`),
    `last_duration_seconds` = VALUES(`last_duration_seconds`);

  SELECT
    `actuator_type`,
    `current_status`,
    `last_activated_at`,
    `cooldown_until`,
    `last_duration_seconds`,
    `updated_at`
  FROM `actuator_runtime_status`
  WHERE `actuator_type` = p_actuator_type;
END$$

CREATE PROCEDURE `sp_get_latest_actuator_status` ()
BEGIN
  UPDATE `actuator_runtime_status`
  SET `current_status` = 'OFF'
  WHERE `current_status` = 'ON'
    AND `last_activated_at` IS NOT NULL
    AND DATE_ADD(`last_activated_at`, INTERVAL `last_duration_seconds` SECOND) <= NOW();

  SELECT
    `log_id`,
    `batch_id`,
    `reading_id`,
    `actuator_type`,
    `status`,
    `trigger_source`,
    `trigger_value`,
    `threshold_value`,
    `duration_seconds`,
    `started_at`,
    `ended_at`,
    `created_at`
  FROM `actuator_logs`
  ORDER BY COALESCE(`started_at`, `created_at`) DESC, `log_id` DESC
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_get_actuator_log_history` ()
BEGIN
  SELECT
    `log_id`,
    `batch_id`,
    `reading_id`,
    `actuator_type`,
    `status`,
    `trigger_source`,
    `trigger_value`,
    `threshold_value`,
    `duration_seconds`,
    `started_at`,
    `ended_at`,
    `created_at`
  FROM `actuator_logs`
  ORDER BY COALESCE(`started_at`, `created_at`) DESC, `log_id` DESC
  LIMIT 100;
END$$

DELIMITER ;
