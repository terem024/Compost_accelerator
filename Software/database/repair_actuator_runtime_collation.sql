-- Select the target database first. Pause sensor submissions while replacing this routine.
-- Matches the project's utf8mb4_general_ci actuator_runtime_status columns.
-- Replaces only this procedure; does not change readings, logs or cooldown values.
SET @actuator_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';
DELIMITER $$
DROP PROCEDURE IF EXISTS `sp_update_actuator_runtime_status`$$
CREATE PROCEDURE `sp_update_actuator_runtime_status` (
  IN `p_actuator_type` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_current_status` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
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
DELIMITER ;
SET SESSION sql_mode = @actuator_previous_sql_mode;

SELECT PARAMETER_NAME, COLLATION_NAME
FROM information_schema.PARAMETERS
WHERE SPECIFIC_SCHEMA = DATABASE()
  AND SPECIFIC_NAME = 'sp_update_actuator_runtime_status'
ORDER BY ORDINAL_POSITION;
