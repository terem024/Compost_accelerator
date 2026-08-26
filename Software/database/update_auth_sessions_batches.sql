-- Authentication sessions, login activity, and active compost batch management.
-- Apply to the existing compost_system database after importing the base dump.

CREATE TABLE IF NOT EXISTS `user_sessions` (
  `session_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `session_token_hash` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `last_seen_at` datetime DEFAULT current_timestamp(),
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','EXPIRED','REVOKED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uq_user_sessions_token_hash` (`session_token_hash`),
  KEY `idx_user_sessions_user_id` (`user_id`),
  KEY `idx_user_sessions_expires_at` (`expires_at`),
  KEY `idx_user_sessions_status` (`status`),
  CONSTRAINT `fk_user_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `login_activity_logs` (
  `login_log_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL,
  `username_or_email` varchar(150) DEFAULT NULL,
  `login_status` enum('SUCCESS','FAILED') NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`login_log_id`),
  KEY `idx_login_activity_user_id` (`user_id`),
  KEY `idx_login_activity_created_at` (`created_at`),
  KEY `idx_login_activity_status` (`login_status`),
  KEY `idx_login_activity_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_login_activity_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE `compost_batches`
  MODIFY `status` enum('ACTIVE','READY_FOR_CHECKING','READY','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS `batch_code` varchar(30) NOT NULL AFTER `batch_id`,
  ADD COLUMN IF NOT EXISTS `batch_name` varchar(100) NOT NULL AFTER `batch_code`,
  ADD COLUMN IF NOT EXISTS `primary_material` varchar(100) NOT NULL AFTER `batch_name`,
  ADD COLUMN IF NOT EXISTS `material_description` text DEFAULT NULL AFTER `primary_material`,
  ADD COLUMN IF NOT EXISTS `start_date` date NOT NULL AFTER `material_description`,
  ADD COLUMN IF NOT EXISTS `expected_duration_days` int(11) DEFAULT NULL AFTER `start_date`,
  ADD COLUMN IF NOT EXISTS `initial_estimated_ready_date` date DEFAULT NULL AFTER `expected_duration_days`,
  ADD COLUMN IF NOT EXISTS `latest_predicted_ready_date` date DEFAULT NULL AFTER `initial_estimated_ready_date`,
  ADD COLUMN IF NOT EXISTS `actual_ready_date` date DEFAULT NULL AFTER `latest_predicted_ready_date`,
  ADD COLUMN IF NOT EXISTS `bin_location` varchar(100) DEFAULT NULL AFTER `status`,
  ADD COLUMN IF NOT EXISTS `notes` text DEFAULT NULL AFTER `bin_location`,
  ADD COLUMN IF NOT EXISTS `created_by` int(11) DEFAULT NULL AFTER `notes`,
  ADD COLUMN IF NOT EXISTS `created_at` timestamp NOT NULL DEFAULT current_timestamp() AFTER `created_by`,
  ADD COLUMN IF NOT EXISTS `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() AFTER `created_at`;

ALTER TABLE `actuator_logs`
  MODIFY `trigger_source` enum('MOISTURE','GAS','AUTO','MANUAL','SAFETY') NOT NULL DEFAULT 'AUTO',
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
  `ended_at` = COALESCE(`ended_at`, DATE_ADD(`created_at`, INTERVAL COALESCE(`duration_seconds`, 5) SECOND)),
  `duration_seconds` = COALESCE(`duration_seconds`, 5)
WHERE `reading_id` IS NULL
   OR `started_at` IS NULL
   OR `ended_at` IS NULL
   OR `duration_seconds` IS NULL;

UPDATE `sensor_readings`
SET `batch_id` = (
  SELECT `batch_id`
  FROM `compost_batches`
  WHERE `status` = 'ACTIVE'
  ORDER BY `start_date` DESC, `batch_id` DESC
  LIMIT 1
)
WHERE `batch_id` IS NULL
  AND EXISTS (
    SELECT 1
    FROM `compost_batches`
    WHERE `status` = 'ACTIVE'
  );

UPDATE `actuator_logs`
SET `batch_id` = (
  SELECT `batch_id`
  FROM `compost_batches`
  WHERE `status` = 'ACTIVE'
  ORDER BY `start_date` DESC, `batch_id` DESC
  LIMIT 1
)
WHERE `batch_id` IS NULL
  AND EXISTS (
    SELECT 1
    FROM `compost_batches`
    WHERE `status` = 'ACTIVE'
  );

CREATE UNIQUE INDEX IF NOT EXISTS `username` ON `users` (`username`);
CREATE INDEX IF NOT EXISTS `idx_user_sessions_user_id` ON `user_sessions` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_user_sessions_token_hash` ON `user_sessions` (`session_token_hash`);
CREATE INDEX IF NOT EXISTS `idx_user_sessions_expires_at` ON `user_sessions` (`expires_at`);
CREATE INDEX IF NOT EXISTS `idx_user_sessions_status` ON `user_sessions` (`status`);
CREATE INDEX IF NOT EXISTS `idx_login_activity_user_id` ON `login_activity_logs` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_login_activity_created_at` ON `login_activity_logs` (`created_at`);
CREATE INDEX IF NOT EXISTS `idx_login_activity_status` ON `login_activity_logs` (`login_status`);
CREATE INDEX IF NOT EXISTS `idx_login_activity_user_created` ON `login_activity_logs` (`user_id`, `created_at`);
CREATE INDEX IF NOT EXISTS `idx_compost_batches_status` ON `compost_batches` (`status`);
CREATE INDEX IF NOT EXISTS `idx_compost_batches_start_date` ON `compost_batches` (`start_date`);
CREATE INDEX IF NOT EXISTS `idx_compost_batches_created_by` ON `compost_batches` (`created_by`);
CREATE INDEX IF NOT EXISTS `idx_sensor_readings_batch_id` ON `sensor_readings` (`batch_id`);
CREATE INDEX IF NOT EXISTS `idx_sensor_readings_created_at` ON `sensor_readings` (`created_at`);
CREATE INDEX IF NOT EXISTS `idx_sensor_readings_batch_created` ON `sensor_readings` (`batch_id`, `created_at`);
CREATE INDEX IF NOT EXISTS `idx_actuator_logs_batch_id` ON `actuator_logs` (`batch_id`);
CREATE INDEX IF NOT EXISTS `idx_actuator_logs_actuator_type` ON `actuator_logs` (`actuator_type`);
CREATE INDEX IF NOT EXISTS `idx_actuator_logs_created_at` ON `actuator_logs` (`created_at`);
CREATE INDEX IF NOT EXISTS `idx_actuator_logs_batch_created` ON `actuator_logs` (`batch_id`, `created_at`);
CREATE INDEX IF NOT EXISTS `idx_ai_predictions_batch_id` ON `ai_predictions` (`batch_id`);
CREATE INDEX IF NOT EXISTS `idx_ai_predictions_created_at` ON `ai_predictions` (`created_at`);
CREATE INDEX IF NOT EXISTS `idx_ai_predictions_batch_created` ON `ai_predictions` (`batch_id`, `created_at`);

DROP PROCEDURE IF EXISTS `sp_register_user`;
DROP PROCEDURE IF EXISTS `sp_login_user`;
DROP PROCEDURE IF EXISTS `sp_create_user_session`;
DROP PROCEDURE IF EXISTS `sp_validate_user_session`;
DROP PROCEDURE IF EXISTS `sp_refresh_user_session`;
DROP PROCEDURE IF EXISTS `sp_logout_user_session`;
DROP PROCEDURE IF EXISTS `sp_record_login_activity`;
DROP PROCEDURE IF EXISTS `sp_create_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_get_active_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_get_compost_batches`;
DROP PROCEDURE IF EXISTS `sp_get_compost_batch_by_id`;
DROP PROCEDURE IF EXISTS `sp_set_active_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_update_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_update_compost_batch_status`;
DROP PROCEDURE IF EXISTS `sp_save_sensor_reading`;
DROP PROCEDURE IF EXISTS `sp_get_latest_sensor_reading`;
DROP PROCEDURE IF EXISTS `sp_get_sensor_reading_history`;
DROP PROCEDURE IF EXISTS `sp_insert_actuator_log`;
DROP PROCEDURE IF EXISTS `sp_save_ai_prediction`;
DROP PROCEDURE IF EXISTS `sp_get_latest_sensor_reading_for_batch`;
DROP PROCEDURE IF EXISTS `sp_get_sensor_reading_summary`;
DROP PROCEDURE IF EXISTS `sp_get_actuator_summary`;

DELIMITER $$

CREATE PROCEDURE `sp_register_user` (
  IN `p_name` VARCHAR(100),
  IN `p_email` VARCHAR(150),
  IN `p_password` VARCHAR(255)
)
BEGIN
  DECLARE `v_name` VARCHAR(100);
  DECLARE `v_username` VARCHAR(150);
  DECLARE `v_salt` VARCHAR(64);
  DECLARE `v_password_hash` VARCHAR(255);

  SET `v_name` = TRIM(COALESCE(`p_name`, ''));
  SET `v_username` = LOWER(TRIM(COALESCE(`p_email`, '')));

  IF `v_name` = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Full name is required.';
  END IF;

  IF `v_username` = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email is required.';
  END IF;

  IF `v_username` NOT REGEXP '^[^[:space:]@]+@[^[:space:]@]+\\.[^[:space:]@]+$' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email must be a valid address.';
  END IF;

  IF TRIM(COALESCE(`p_password`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Password is required.';
  END IF;

  IF CHAR_LENGTH(`p_password`) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Password must be at least 8 characters.';
  END IF;

  IF EXISTS (SELECT 1 FROM `users` WHERE LOWER(`username`) = `v_username`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email is already registered.';
  END IF;

  SET `v_salt` = SHA2(CONCAT(UUID(), RAND(), NOW(6)), 256);
  SET `v_password_hash` = SHA2(CONCAT(`v_salt`, `p_password`), 256);

  INSERT INTO `users` (`full_name`, `username`, `password_hash`, `password_salt`, `role`)
  VALUES (`v_name`, `v_username`, `v_password_hash`, `v_salt`, 'OPERATOR');

  SELECT
    `user_id`,
    `full_name` AS `name`,
    `username` AS `email`,
    `role`
  FROM `users`
  WHERE `user_id` = LAST_INSERT_ID();
END$$

CREATE PROCEDURE `sp_login_user` (
  IN `p_username_or_email` VARCHAR(150),
  IN `p_password` VARCHAR(255)
)
BEGIN
  DECLARE `v_identifier` VARCHAR(150);

  SET `v_identifier` = LOWER(TRIM(COALESCE(`p_username_or_email`, '')));

  IF `v_identifier` = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email or username is required.';
  END IF;

  IF TRIM(COALESCE(`p_password`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Password is required.';
  END IF;

  SELECT
    `user_id`,
    `full_name` AS `name`,
    `username` AS `email`,
    `role`
  FROM `users`
  WHERE LOWER(`username`) = `v_identifier`
    AND `password_salt` IS NOT NULL
    AND `password_hash` = SHA2(CONCAT(`password_salt`, `p_password`), 256)
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_create_user_session` (
  IN `p_user_id` INT,
  IN `p_session_token_hash` CHAR(64),
  IN `p_expires_at` DATETIME,
  IN `p_ip_address` VARCHAR(45),
  IN `p_user_agent` VARCHAR(255)
)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `users` WHERE `user_id` = `p_user_id`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User account was not found.';
  END IF;

  INSERT INTO `user_sessions`
    (`user_id`, `session_token_hash`, `expires_at`, `ip_address`, `user_agent`, `status`)
  VALUES
    (`p_user_id`, `p_session_token_hash`, `p_expires_at`, `p_ip_address`, LEFT(`p_user_agent`, 255), 'ACTIVE');

  SELECT
    `s`.`session_id`,
    `s`.`user_id`,
    `s`.`expires_at`,
    `s`.`last_seen_at`,
    `s`.`status`,
    `u`.`full_name` AS `name`,
    `u`.`username` AS `email`,
    `u`.`role`
  FROM `user_sessions` `s`
  JOIN `users` `u` ON `u`.`user_id` = `s`.`user_id`
  WHERE `s`.`session_id` = LAST_INSERT_ID();
END$$

CREATE PROCEDURE `sp_validate_user_session` (
  IN `p_session_token_hash` CHAR(64)
)
BEGIN
  UPDATE `user_sessions`
  SET `status` = 'EXPIRED'
  WHERE `status` = 'ACTIVE'
    AND `expires_at` <= NOW();

  UPDATE `user_sessions`
  SET `last_seen_at` = NOW()
  WHERE `session_token_hash` = `p_session_token_hash`
    AND `status` = 'ACTIVE'
    AND `revoked_at` IS NULL
    AND `expires_at` > NOW();

  SELECT
    `s`.`session_id`,
    `s`.`user_id`,
    `s`.`expires_at`,
    `s`.`last_seen_at`,
    `s`.`status`,
    `u`.`full_name` AS `name`,
    `u`.`username` AS `email`,
    `u`.`role`
  FROM `user_sessions` `s`
  JOIN `users` `u` ON `u`.`user_id` = `s`.`user_id`
  WHERE `s`.`session_token_hash` = `p_session_token_hash`
    AND `s`.`status` = 'ACTIVE'
    AND `s`.`revoked_at` IS NULL
    AND `s`.`expires_at` > NOW()
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_refresh_user_session` (
  IN `p_session_token_hash` CHAR(64),
  IN `p_expires_at` DATETIME
)
BEGIN
  UPDATE `user_sessions`
  SET `status` = 'EXPIRED'
  WHERE `status` = 'ACTIVE'
    AND `expires_at` <= NOW();

  UPDATE `user_sessions`
  SET
    `expires_at` = `p_expires_at`,
    `last_seen_at` = NOW()
  WHERE `session_token_hash` = `p_session_token_hash`
    AND `status` = 'ACTIVE'
    AND `revoked_at` IS NULL
    AND `expires_at` > NOW();

  SELECT
    `s`.`session_id`,
    `s`.`user_id`,
    `s`.`expires_at`,
    `s`.`last_seen_at`,
    `s`.`status`,
    `u`.`full_name` AS `name`,
    `u`.`username` AS `email`,
    `u`.`role`
  FROM `user_sessions` `s`
  JOIN `users` `u` ON `u`.`user_id` = `s`.`user_id`
  WHERE `s`.`session_token_hash` = `p_session_token_hash`
    AND `s`.`status` = 'ACTIVE'
    AND `s`.`revoked_at` IS NULL
    AND `s`.`expires_at` > NOW()
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_logout_user_session` (
  IN `p_session_token_hash` CHAR(64)
)
BEGIN
  UPDATE `user_sessions`
  SET
    `status` = 'REVOKED',
    `revoked_at` = NOW(),
    `last_seen_at` = NOW()
  WHERE `session_token_hash` = `p_session_token_hash`
    AND `status` = 'ACTIVE';

  SELECT ROW_COUNT() AS `affected_rows`;
END$$

CREATE PROCEDURE `sp_record_login_activity` (
  IN `p_user_id` INT,
  IN `p_username_or_email` VARCHAR(150),
  IN `p_login_status` VARCHAR(20),
  IN `p_failure_reason` VARCHAR(255),
  IN `p_ip_address` VARCHAR(45),
  IN `p_user_agent` VARCHAR(255)
)
BEGIN
  INSERT INTO `login_activity_logs`
    (`user_id`, `username_or_email`, `login_status`, `failure_reason`, `ip_address`, `user_agent`)
  VALUES
    (`p_user_id`, LEFT(TRIM(COALESCE(`p_username_or_email`, '')), 150), `p_login_status`, `p_failure_reason`, `p_ip_address`, LEFT(`p_user_agent`, 255));
END$$

CREATE PROCEDURE `sp_get_active_compost_batch` ()
BEGIN
  SELECT
    `batch_id`,
    `batch_code`,
    `batch_name`,
    `primary_material`,
    `material_description`,
    `start_date`,
    `expected_duration_days`,
    `initial_estimated_ready_date`,
    `latest_predicted_ready_date`,
    `actual_ready_date`,
    `status`,
    `bin_location`,
    `notes`,
    `created_by`,
    `created_at`,
    `updated_at`
  FROM `compost_batches`
  WHERE `status` = 'ACTIVE'
  ORDER BY `start_date` DESC, `batch_id` DESC
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_get_compost_batches` ()
BEGIN
  SELECT
    `batch_id`,
    `batch_code`,
    `batch_name`,
    `primary_material`,
    `material_description`,
    `start_date`,
    `expected_duration_days`,
    `initial_estimated_ready_date`,
    `latest_predicted_ready_date`,
    `actual_ready_date`,
    `status`,
    `bin_location`,
    `notes`,
    `created_by`,
    `created_at`,
    `updated_at`
  FROM `compost_batches`
  ORDER BY FIELD(`status`, 'ACTIVE', 'READY', 'READY_FOR_CHECKING', 'COMPLETED', 'CANCELLED'),
           `start_date` DESC,
           `batch_id` DESC;
END$$

CREATE PROCEDURE `sp_get_compost_batch_by_id` (
  IN `p_batch_id` INT
)
BEGIN
  SELECT
    `batch_id`,
    `batch_code`,
    `batch_name`,
    `primary_material`,
    `material_description`,
    `start_date`,
    `expected_duration_days`,
    `initial_estimated_ready_date`,
    `latest_predicted_ready_date`,
    `actual_ready_date`,
    `status`,
    `bin_location`,
    `notes`,
    `created_by`,
    `created_at`,
    `updated_at`
  FROM `compost_batches`
  WHERE `batch_id` = `p_batch_id`
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_create_compost_batch` (
  IN `p_batch_name` VARCHAR(100),
  IN `p_primary_material` VARCHAR(100),
  IN `p_material_description` TEXT,
  IN `p_start_date` DATE,
  IN `p_expected_duration_days` INT,
  IN `p_bin_location` VARCHAR(100),
  IN `p_notes` TEXT,
  IN `p_created_by` INT
)
BEGIN
  DECLARE `v_next_id` INT DEFAULT 1;
  DECLARE `v_batch_code` VARCHAR(30);
  DECLARE `v_initial_ready_date` DATE DEFAULT NULL;

  IF TRIM(COALESCE(`p_batch_name`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Batch name is required.';
  END IF;

  IF TRIM(COALESCE(`p_primary_material`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Primary material is required.';
  END IF;

  IF `p_start_date` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Start date is required.';
  END IF;

  IF `p_expected_duration_days` IS NOT NULL AND `p_expected_duration_days` <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected duration must be greater than zero.';
  END IF;

  SELECT COALESCE(`AUTO_INCREMENT`, 1)
  INTO `v_next_id`
  FROM `information_schema`.`TABLES`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'compost_batches';

  SET `v_batch_code` = CONCAT('BATCH-', LPAD(`v_next_id`, 3, '0'));

  WHILE EXISTS (SELECT 1 FROM `compost_batches` WHERE `batch_code` = `v_batch_code`) DO
    SET `v_next_id` = `v_next_id` + 1;
    SET `v_batch_code` = CONCAT('BATCH-', LPAD(`v_next_id`, 3, '0'));
  END WHILE;

  IF `p_expected_duration_days` IS NOT NULL THEN
    SET `v_initial_ready_date` = DATE_ADD(`p_start_date`, INTERVAL `p_expected_duration_days` DAY);
  END IF;

  UPDATE `compost_batches`
  SET `status` = 'READY_FOR_CHECKING'
  WHERE `status` = 'ACTIVE';

  INSERT INTO `compost_batches`
    (
      `batch_code`,
      `batch_name`,
      `primary_material`,
      `material_description`,
      `start_date`,
      `expected_duration_days`,
      `initial_estimated_ready_date`,
      `status`,
      `bin_location`,
      `notes`,
      `created_by`
    )
  VALUES
    (
      `v_batch_code`,
      TRIM(`p_batch_name`),
      TRIM(`p_primary_material`),
      NULLIF(TRIM(COALESCE(`p_material_description`, '')), ''),
      `p_start_date`,
      `p_expected_duration_days`,
      `v_initial_ready_date`,
      'ACTIVE',
      NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
      NULLIF(TRIM(COALESCE(`p_notes`, '')), ''),
      `p_created_by`
    );

  CALL `sp_get_compost_batch_by_id`(LAST_INSERT_ID());
END$$

CREATE PROCEDURE `sp_set_active_compost_batch` (
  IN `p_batch_id` INT
)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `compost_batches` WHERE `batch_id` = `p_batch_id`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch was not found.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM `compost_batches`
    WHERE `batch_id` = `p_batch_id`
      AND `status` IN ('COMPLETED', 'CANCELLED')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed or cancelled batches cannot be set active.';
  END IF;

  UPDATE `compost_batches`
  SET `status` = 'READY_FOR_CHECKING'
  WHERE `status` = 'ACTIVE'
    AND `batch_id` <> `p_batch_id`;

  UPDATE `compost_batches`
  SET `status` = 'ACTIVE'
  WHERE `batch_id` = `p_batch_id`;

  CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
END$$

CREATE PROCEDURE `sp_update_compost_batch` (
  IN `p_batch_id` INT,
  IN `p_batch_name` VARCHAR(100),
  IN `p_primary_material` VARCHAR(100),
  IN `p_material_description` TEXT,
  IN `p_start_date` DATE,
  IN `p_expected_duration_days` INT,
  IN `p_bin_location` VARCHAR(100),
  IN `p_notes` TEXT
)
BEGIN
  DECLARE `v_initial_ready_date` DATE DEFAULT NULL;

  IF NOT EXISTS (SELECT 1 FROM `compost_batches` WHERE `batch_id` = `p_batch_id`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch was not found.';
  END IF;

  IF TRIM(COALESCE(`p_batch_name`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Batch name is required.';
  END IF;

  IF TRIM(COALESCE(`p_primary_material`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Primary material is required.';
  END IF;

  IF `p_start_date` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Start date is required.';
  END IF;

  IF `p_expected_duration_days` IS NOT NULL AND `p_expected_duration_days` <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected duration must be greater than zero.';
  END IF;

  IF `p_expected_duration_days` IS NOT NULL THEN
    SET `v_initial_ready_date` = DATE_ADD(`p_start_date`, INTERVAL `p_expected_duration_days` DAY);
  END IF;

  UPDATE `compost_batches`
  SET
    `batch_name` = TRIM(`p_batch_name`),
    `primary_material` = TRIM(`p_primary_material`),
    `material_description` = NULLIF(TRIM(COALESCE(`p_material_description`, '')), ''),
    `start_date` = `p_start_date`,
    `expected_duration_days` = `p_expected_duration_days`,
    `initial_estimated_ready_date` = `v_initial_ready_date`,
    `bin_location` = NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
    `notes` = NULLIF(TRIM(COALESCE(`p_notes`, '')), '')
  WHERE `batch_id` = `p_batch_id`;

  CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
END$$

CREATE PROCEDURE `sp_update_compost_batch_status` (
  IN `p_batch_id` INT,
  IN `p_status` VARCHAR(30),
  IN `p_actual_ready_date` DATE
)
BEGIN
  IF `p_status` = 'ACTIVE' THEN
    CALL `sp_set_active_compost_batch`(`p_batch_id`);
  ELSE
    IF `p_status` NOT IN ('READY_FOR_CHECKING', 'READY', 'COMPLETED', 'CANCELLED') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid compost batch status.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `compost_batches` WHERE `batch_id` = `p_batch_id`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch was not found.';
    END IF;

    UPDATE `compost_batches`
    SET
      `status` = `p_status`,
      `actual_ready_date` = CASE
        WHEN `p_status` IN ('READY', 'COMPLETED') THEN COALESCE(`p_actual_ready_date`, `actual_ready_date`, CURDATE())
        ELSE `actual_ready_date`
      END
    WHERE `batch_id` = `p_batch_id`;

    CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
  END IF;
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
  DECLARE `v_gas_max` DECIMAL(8,2) DEFAULT 1200.00;
  DECLARE `v_moisture_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_gas_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_temperature_status` VARCHAR(10) DEFAULT 'NORMAL';
  DECLARE `v_humidity_status` VARCHAR(10) DEFAULT 'NORMAL';

  SET `v_batch_id` = `p_batch_id`;

  IF `v_batch_id` IS NULL THEN
    SET `v_batch_id` = (
      SELECT `batch_id`
      FROM `compost_batches`
      WHERE `status` = 'ACTIVE'
      ORDER BY `start_date` DESC, `batch_id` DESC
      LIMIT 1
    );
  END IF;

  IF `v_batch_id` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No active compost batch found. Create or activate a compost batch before saving sensor readings.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `compost_batches` WHERE `batch_id` = `v_batch_id`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selected compost batch was not found.';
  END IF;

  IF `p_moisture_level` IS NULL OR `p_gas_level` IS NULL OR `p_temperature_c` IS NULL OR `p_humidity_level` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'All sensor values are required.';
  END IF;

  IF EXISTS (SELECT 1 FROM `threshold_settings`) THEN
    SELECT `moisture_min`, `gas_max`
    INTO `v_moisture_min`, `v_gas_max`
    FROM `threshold_settings`
    ORDER BY `setting_id` DESC
    LIMIT 1;
  END IF;

  SET `v_moisture_status` = CASE
    WHEN `p_moisture_level` < `v_moisture_min` THEN 'LOW'
    WHEN `p_moisture_level` > 70.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_gas_status` = CASE
    WHEN `p_gas_level` < 800.00 THEN 'LOW'
    WHEN `p_gas_level` > `v_gas_max` THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_temperature_status` = CASE
    WHEN `p_temperature_c` < 30.00 THEN 'LOW'
    WHEN `p_temperature_c` > 50.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;

  SET `v_humidity_status` = CASE
    WHEN `p_humidity_level` < 40.00 THEN 'LOW'
    WHEN `p_humidity_level` > 70.00 THEN 'HIGH'
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
      `p_moisture_level`,
      `p_gas_level`,
      `p_temperature_c`,
      `p_humidity_level`,
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

  SET `v_batch_id` = (
    SELECT `batch_id`
    FROM `compost_batches`
    WHERE `status` = 'ACTIVE'
    ORDER BY `start_date` DESC, `batch_id` DESC
    LIMIT 1
  );

  IF `v_batch_id` IS NULL THEN
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
    WHERE `batch_id` = `v_batch_id`
    ORDER BY `created_at` DESC, `reading_id` DESC
    LIMIT 1;
  END IF;
END$$

CREATE PROCEDURE `sp_get_sensor_reading_history` ()
BEGIN
  DECLARE `v_batch_id` INT DEFAULT NULL;

  SET `v_batch_id` = (
    SELECT `batch_id`
    FROM `compost_batches`
    WHERE `status` = 'ACTIVE'
    ORDER BY `start_date` DESC, `batch_id` DESC
    LIMIT 1
  );

  IF `v_batch_id` IS NULL THEN
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
    WHERE `batch_id` = `v_batch_id`
    ORDER BY `created_at` DESC, `reading_id` DESC
    LIMIT 100;
  END IF;
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
  DECLARE `v_batch_id` INT DEFAULT NULL;

  SET `v_batch_id` = `p_batch_id`;

  IF `v_batch_id` IS NULL THEN
    SET `v_batch_id` = (
      SELECT `batch_id`
      FROM `compost_batches`
      WHERE `status` = 'ACTIVE'
      ORDER BY `start_date` DESC, `batch_id` DESC
      LIMIT 1
    );
  END IF;

  IF `v_batch_id` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No active compost batch found. Create or activate a compost batch before logging actuator activity.';
  END IF;

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
      `v_batch_id`,
      `p_reading_id`,
      `p_actuator_type`,
      `p_status`,
      `p_trigger_source`,
      `p_trigger_value`,
      `p_threshold_value`,
      `p_duration_seconds`,
      `p_started_at`,
      `p_ended_at`,
      `p_reading_id`,
      CONCAT(`p_trigger_source`, ' threshold pulse activation')
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

CREATE PROCEDURE `sp_get_latest_sensor_reading_for_batch` (
  IN `p_batch_id` INT
)
BEGIN
  SELECT
    `reading_id`,
    `batch_id`,
    `moisture_level`,
    `gas_level`,
    `temperature_c`,
    `temperature_status`,
    `humidity_level`,
    `humidity_status`,
    `moisture_status`,
    `gas_status`,
    `created_at`
  FROM `sensor_readings`
  WHERE `batch_id` = `p_batch_id`
  ORDER BY `created_at` DESC, `reading_id` DESC
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_get_sensor_reading_summary` (
  IN `p_batch_id` INT,
  IN `p_window_start` DATETIME
)
BEGIN
  SELECT
    COUNT(*) AS `total_readings`,
    MIN(`created_at`) AS `analysis_window_start`,
    MAX(`created_at`) AS `analysis_window_end`,
    AVG(`moisture_level`) AS `avg_moisture`,
    MIN(`moisture_level`) AS `min_moisture`,
    MAX(`moisture_level`) AS `max_moisture`,
    SUM(CASE WHEN `moisture_status` = 'LOW' THEN 1 ELSE 0 END) AS `low_moisture_count`,
    SUM(CASE WHEN `moisture_status` = 'HIGH' THEN 1 ELSE 0 END) AS `high_moisture_count`,
    AVG(`gas_level`) AS `avg_gas`,
    MIN(`gas_level`) AS `min_gas`,
    MAX(`gas_level`) AS `max_gas`,
    SUM(CASE WHEN `gas_status` = 'HIGH' THEN 1 ELSE 0 END) AS `high_gas_count`,
    AVG(`temperature_c`) AS `avg_temperature`,
    MIN(`temperature_c`) AS `min_temperature`,
    MAX(`temperature_c`) AS `max_temperature`,
    SUM(CASE WHEN `temperature_status` = 'LOW' THEN 1 ELSE 0 END) AS `low_temperature_count`,
    SUM(CASE WHEN `temperature_status` = 'HIGH' THEN 1 ELSE 0 END) AS `high_temperature_count`,
    AVG(`humidity_level`) AS `avg_humidity`,
    MIN(`humidity_level`) AS `min_humidity`,
    MAX(`humidity_level`) AS `max_humidity`,
    SUM(CASE WHEN `humidity_status` = 'LOW' THEN 1 ELSE 0 END) AS `low_humidity_count`,
    SUM(CASE WHEN `humidity_status` = 'HIGH' THEN 1 ELSE 0 END) AS `high_humidity_count`
  FROM `sensor_readings`
  WHERE `batch_id` = `p_batch_id`
    AND `created_at` >= `p_window_start`;
END$$

CREATE PROCEDURE `sp_get_actuator_summary` (
  IN `p_batch_id` INT,
  IN `p_window_start` DATETIME
)
BEGIN
  SELECT
    `actuator_type`,
    `status`,
    `trigger_source`,
    COUNT(*) AS `total_events`
  FROM `actuator_logs`
  WHERE `batch_id` = `p_batch_id`
    AND `created_at` >= `p_window_start`
  GROUP BY `actuator_type`, `status`, `trigger_source`
  ORDER BY `actuator_type`, `status`, `trigger_source`;
END$$

CREATE PROCEDURE `sp_save_ai_prediction` (
  IN `p_batch_id` INT,
  IN `p_reading_id` INT,
  IN `p_predicted_condition` VARCHAR(40),
  IN `p_prediction_summary` TEXT,
  IN `p_estimated_ready_date` DATE,
  IN `p_estimated_days_remaining` INT,
  IN `p_recommendation` TEXT,
  IN `p_trend_summary` TEXT,
  IN `p_analysis_window_start` DATETIME,
  IN `p_analysis_window_end` DATETIME,
  IN `p_confidence_score` DECIMAL(5,2),
  IN `p_model_name` VARCHAR(100),
  IN `p_input_snapshot` LONGTEXT,
  IN `p_raw_ai_response` LONGTEXT
)
BEGIN
  INSERT INTO `ai_predictions`
    (
      `batch_id`,
      `prediction_type`,
      `reading_id`,
      `predicted_condition`,
      `prediction_summary`,
      `estimated_ready_date`,
      `estimated_days_remaining`,
      `recommendation`,
      `trend_summary`,
      `analysis_window_start`,
      `analysis_window_end`,
      `confidence_score`,
      `model_provider`,
      `model_name`,
      `input_snapshot`,
      `raw_ai_response`
    )
  VALUES
    (
      `p_batch_id`,
      'READINESS_ESTIMATE',
      `p_reading_id`,
      `p_predicted_condition`,
      `p_prediction_summary`,
      `p_estimated_ready_date`,
      `p_estimated_days_remaining`,
      `p_recommendation`,
      `p_trend_summary`,
      `p_analysis_window_start`,
      `p_analysis_window_end`,
      `p_confidence_score`,
      'Gemini',
      `p_model_name`,
      `p_input_snapshot`,
      `p_raw_ai_response`
    );

  IF `p_estimated_ready_date` IS NOT NULL THEN
    UPDATE `compost_batches`
    SET `latest_predicted_ready_date` = `p_estimated_ready_date`
    WHERE `batch_id` = `p_batch_id`;
  END IF;

  SELECT LAST_INSERT_ID() AS `prediction_id`;
END$$

DELIMITER ;
