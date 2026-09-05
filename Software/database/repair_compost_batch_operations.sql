-- Select the target database before sourcing this file (for Railway: USE railway;).
-- Repairs the complete Batch & Actuator Controls batch workflow without deleting batch data.
-- The routines match the current 5 kg (HALF) and 10 kg (FULL) application contract.

SET @add_fill_level = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'compost_batches'
      AND COLUMN_NAME = 'fill_level'
  ),
  'SELECT 1',
  'ALTER TABLE `compost_batches` ADD COLUMN `fill_level` ENUM(''ONE_THIRD'', ''HALF'', ''FULL'') NOT NULL DEFAULT ''HALF'' AFTER `material_description`'
);
PREPARE add_fill_level_statement FROM @add_fill_level;
EXECUTE add_fill_level_statement;
DEALLOCATE PREPARE add_fill_level_statement;

ALTER TABLE `compost_batches`
  MODIFY `status`
    ENUM('ACTIVE', 'READY_FOR_CHECKING', 'READY', 'COMPLETED', 'CANCELLED')
    NOT NULL DEFAULT 'ACTIVE';

SET @batch_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

DROP PROCEDURE IF EXISTS `sp_get_compost_batch_by_id`;
DROP PROCEDURE IF EXISTS `sp_create_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_update_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_set_active_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_update_compost_batch_status`;

DELIMITER $$

CREATE PROCEDURE `sp_get_compost_batch_by_id` (IN `p_batch_id` INT)
BEGIN
  SELECT
    cb.`batch_id`,
    cb.`batch_code`,
    cb.`batch_name`,
    cb.`primary_material`,
    cb.`material_description`,
    cb.`fill_level`,
    cb.`start_date`,
    (
      SELECT ap.`estimated_ready_date`
      FROM `ai_predictions` ap
      WHERE ap.`batch_id` = cb.`batch_id`
      ORDER BY ap.`created_at` DESC, ap.`prediction_id` DESC
      LIMIT 1
    ) AS `latest_predicted_ready_date`,
    cb.`actual_ready_date`,
    cb.`status`,
    cb.`bin_location`,
    cb.`notes`,
    cb.`created_by`,
    cb.`created_at`,
    cb.`updated_at`
  FROM `compost_batches` cb
  WHERE cb.`batch_id` = `p_batch_id`
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_create_compost_batch` (
  IN `p_batch_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_primary_material` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_material_description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_fill_level` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_start_date` DATE,
  IN `p_bin_location` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_notes` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_created_by` INT
)
BEGIN
  DECLARE `v_batch_id` INT;
  DECLARE `v_fill_level` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE `v_pending_code` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF TRIM(COALESCE(`p_batch_name`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Batch name is required.';
  END IF;
  IF TRIM(COALESCE(`p_primary_material`, '')) = '' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Primary material is required.';
  END IF;
  IF `p_start_date` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Start date is required.';
  END IF;

  SET `v_fill_level` = UPPER(TRIM(COALESCE(`p_fill_level`, 'HALF')));
  IF `v_fill_level` NOT IN ('HALF', 'FULL') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch weight must be 5 kg or 10 kg.';
  END IF;

  SET `v_pending_code` = CONCAT('TMP-', SUBSTRING(REPLACE(UUID(), '-', ''), 1, 26));

  START TRANSACTION;
  UPDATE `compost_batches`
  SET `status` = 'READY_FOR_CHECKING'
  WHERE `status` = 'ACTIVE';

  INSERT INTO `compost_batches`
    (`batch_code`, `batch_name`, `primary_material`, `material_description`, `fill_level`,
     `start_date`, `status`, `bin_location`, `notes`, `created_by`)
  VALUES
    (`v_pending_code`, TRIM(`p_batch_name`), TRIM(`p_primary_material`),
     NULLIF(TRIM(COALESCE(`p_material_description`, '')), ''), `v_fill_level`,
     `p_start_date`, 'ACTIVE', NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
     NULLIF(TRIM(COALESCE(`p_notes`, '')), ''), `p_created_by`);

  SET `v_batch_id` = LAST_INSERT_ID();
  UPDATE `compost_batches`
  SET `batch_code` = CONCAT('BATCH-', LPAD(`v_batch_id`, 3, '0'))
  WHERE `batch_id` = `v_batch_id`;
  COMMIT;

  CALL `sp_get_compost_batch_by_id`(`v_batch_id`);
END$$

CREATE PROCEDURE `sp_update_compost_batch` (
  IN `p_batch_id` INT,
  IN `p_batch_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_primary_material` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_material_description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_fill_level` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_start_date` DATE,
  IN `p_bin_location` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_notes` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
)
BEGIN
  DECLARE `v_fill_level` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

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

  SET `v_fill_level` = UPPER(TRIM(COALESCE(`p_fill_level`, 'HALF')));
  IF `v_fill_level` NOT IN ('HALF', 'FULL') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch weight must be 5 kg or 10 kg.';
  END IF;

  START TRANSACTION;
  UPDATE `compost_batches`
  SET `batch_name` = TRIM(`p_batch_name`),
      `primary_material` = TRIM(`p_primary_material`),
      `material_description` = NULLIF(TRIM(COALESCE(`p_material_description`, '')), ''),
      `fill_level` = `v_fill_level`,
      `start_date` = `p_start_date`,
      `bin_location` = NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
      `notes` = NULLIF(TRIM(COALESCE(`p_notes`, '')), '')
  WHERE `batch_id` = `p_batch_id`;
  COMMIT;

  CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
END$$

CREATE PROCEDURE `sp_set_active_compost_batch` (IN `p_batch_id` INT)
BEGIN
  DECLARE `v_status` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT `status` INTO `v_status`
  FROM `compost_batches`
  WHERE `batch_id` = `p_batch_id`
  LIMIT 1;

  IF `v_status` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch was not found.';
  END IF;
  IF `v_status` IN ('COMPLETED', 'CANCELLED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed or cancelled batches cannot be set active.';
  END IF;

  START TRANSACTION;
  UPDATE `compost_batches`
  SET `status` = 'READY_FOR_CHECKING'
  WHERE `status` = 'ACTIVE' AND `batch_id` <> `p_batch_id`;

  UPDATE `compost_batches`
  SET `status` = 'ACTIVE',
      `actual_ready_date` = NULL
  WHERE `batch_id` = `p_batch_id`;
  COMMIT;

  CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
END$$

CREATE PROCEDURE `sp_update_compost_batch_status` (
  IN `p_batch_id` INT,
  IN `p_status` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_actual_ready_date` DATE
)
BEGIN
  DECLARE `v_current_status` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE `v_status` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SET `v_status` = UPPER(TRIM(COALESCE(`p_status`, '')));
  IF `v_status` NOT IN ('ACTIVE', 'READY_FOR_CHECKING', 'READY', 'COMPLETED', 'CANCELLED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid compost batch status.';
  END IF;

  SELECT `status` INTO `v_current_status`
  FROM `compost_batches`
  WHERE `batch_id` = `p_batch_id`
  LIMIT 1;

  IF `v_current_status` IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Compost batch was not found.';
  END IF;
  IF `v_current_status` IN ('COMPLETED', 'CANCELLED') AND `v_status` <> `v_current_status` THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed or cancelled batches cannot be changed.';
  END IF;

  IF `v_status` = 'ACTIVE' THEN
    CALL `sp_set_active_compost_batch`(`p_batch_id`);
  ELSE
    START TRANSACTION;
    UPDATE `compost_batches`
    SET `status` = `v_status`,
        `actual_ready_date` = CASE
          WHEN `v_status` IN ('READY', 'COMPLETED')
            THEN COALESCE(`p_actual_ready_date`, `actual_ready_date`, CURDATE())
          ELSE `actual_ready_date`
        END
    WHERE `batch_id` = `p_batch_id`;
    COMMIT;

    CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
  END IF;
END$$

DELIMITER ;
SET SESSION sql_mode = @batch_previous_sql_mode;

SELECT SPECIFIC_NAME, PARAMETER_NAME, ORDINAL_POSITION, DATA_TYPE, COLLATION_NAME
FROM information_schema.PARAMETERS
WHERE SPECIFIC_SCHEMA = DATABASE()
  AND SPECIFIC_NAME IN (
    'sp_get_compost_batch_by_id',
    'sp_create_compost_batch',
    'sp_update_compost_batch',
    'sp_set_active_compost_batch',
    'sp_update_compost_batch_status'
  )
ORDER BY SPECIFIC_NAME, ORDINAL_POSITION;
