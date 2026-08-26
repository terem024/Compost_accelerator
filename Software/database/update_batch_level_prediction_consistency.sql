USE `compost_system`;

ALTER TABLE `compost_batches`
  ADD COLUMN IF NOT EXISTS `fill_level`
    ENUM('ONE_THIRD', 'HALF', 'FULL') NOT NULL DEFAULT 'HALF'
    AFTER `material_description`;

ALTER TABLE `compost_batches`
  DROP COLUMN IF EXISTS `expected_duration_days`,
  DROP COLUMN IF EXISTS `initial_estimated_ready_date`;

UPDATE `threshold_settings`
SET
  `spray_duration_seconds` = 10,
  `fan_duration_seconds` = 10
WHERE `setting_id` = (
  SELECT `latest`.`setting_id`
  FROM (
    SELECT MAX(`setting_id`) AS `setting_id`
    FROM `threshold_settings`
  ) `latest`
);

DROP PROCEDURE IF EXISTS `sp_get_active_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_get_compost_batches`;
DROP PROCEDURE IF EXISTS `sp_get_compost_batch_by_id`;
DROP PROCEDURE IF EXISTS `sp_create_compost_batch`;
DROP PROCEDURE IF EXISTS `sp_update_compost_batch`;

DELIMITER $$

CREATE PROCEDURE `sp_get_active_compost_batch` ()
BEGIN
  SELECT
    `batch_id`,
    `batch_code`,
    `batch_name`,
    `primary_material`,
    `material_description`,
    `fill_level`,
    `start_date`,
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
    `fill_level`,
    `start_date`,
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
    `fill_level`,
    `start_date`,
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
  IN `p_fill_level` VARCHAR(20),
  IN `p_start_date` DATE,
  IN `p_bin_location` VARCHAR(100),
  IN `p_notes` TEXT,
  IN `p_created_by` INT
)
BEGIN
  DECLARE `v_next_id` INT DEFAULT 1;
  DECLARE `v_batch_code` VARCHAR(30);
  DECLARE `v_fill_level` VARCHAR(20);

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
  IF `v_fill_level` NOT IN ('ONE_THIRD', 'HALF', 'FULL') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fill level must be ONE_THIRD, HALF, or FULL.';
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

  UPDATE `compost_batches`
  SET `status` = 'READY_FOR_CHECKING'
  WHERE `status` = 'ACTIVE';

  INSERT INTO `compost_batches`
    (
      `batch_code`,
      `batch_name`,
      `primary_material`,
      `material_description`,
      `fill_level`,
      `start_date`,
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
      `v_fill_level`,
      `p_start_date`,
      'ACTIVE',
      NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
      NULLIF(TRIM(COALESCE(`p_notes`, '')), ''),
      `p_created_by`
    );

  CALL `sp_get_compost_batch_by_id`(LAST_INSERT_ID());
END$$

CREATE PROCEDURE `sp_update_compost_batch` (
  IN `p_batch_id` INT,
  IN `p_batch_name` VARCHAR(100),
  IN `p_primary_material` VARCHAR(100),
  IN `p_material_description` TEXT,
  IN `p_fill_level` VARCHAR(20),
  IN `p_start_date` DATE,
  IN `p_bin_location` VARCHAR(100),
  IN `p_notes` TEXT
)
BEGIN
  DECLARE `v_fill_level` VARCHAR(20);

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
  IF `v_fill_level` NOT IN ('ONE_THIRD', 'HALF', 'FULL') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fill level must be ONE_THIRD, HALF, or FULL.';
  END IF;

  UPDATE `compost_batches`
  SET
    `batch_name` = TRIM(`p_batch_name`),
    `primary_material` = TRIM(`p_primary_material`),
    `material_description` = NULLIF(TRIM(COALESCE(`p_material_description`, '')), ''),
    `fill_level` = `v_fill_level`,
    `start_date` = `p_start_date`,
    `bin_location` = NULLIF(TRIM(COALESCE(`p_bin_location`, '')), ''),
    `notes` = NULLIF(TRIM(COALESCE(`p_notes`, '')), '')
  WHERE `batch_id` = `p_batch_id`;

  CALL `sp_get_compost_batch_by_id`(`p_batch_id`);
END$$

DELIMITER ;
