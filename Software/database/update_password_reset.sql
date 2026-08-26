-- Gmail-backed forgot password and reset password support.
-- Apply to the existing compost_system database.

CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `reset_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `request_ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','USED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`reset_id`),
  UNIQUE KEY `uq_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_user_id` (`user_id`),
  KEY `idx_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_expires_at` (`expires_at`),
  KEY `idx_password_reset_status` (`status`),
  CONSTRAINT `fk_password_reset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX IF NOT EXISTS `idx_password_reset_user_id` ON `password_reset_tokens` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_password_reset_token_hash` ON `password_reset_tokens` (`token_hash`);
CREATE INDEX IF NOT EXISTS `idx_password_reset_expires_at` ON `password_reset_tokens` (`expires_at`);
CREATE INDEX IF NOT EXISTS `idx_password_reset_status` ON `password_reset_tokens` (`status`);

DROP PROCEDURE IF EXISTS `sp_create_password_reset_token`;
DROP PROCEDURE IF EXISTS `sp_get_user_by_email`;
DROP PROCEDURE IF EXISTS `sp_validate_password_reset_token`;
DROP PROCEDURE IF EXISTS `sp_update_user_password`;
DROP PROCEDURE IF EXISTS `sp_mark_password_reset_token_used`;
DROP PROCEDURE IF EXISTS `sp_expire_old_password_reset_tokens`;

DELIMITER $$

CREATE PROCEDURE `sp_expire_old_password_reset_tokens` ()
BEGIN
  UPDATE `password_reset_tokens`
  SET `status` = 'EXPIRED'
  WHERE `status` = 'ACTIVE'
    AND `expires_at` <= NOW();
END$$

CREATE PROCEDURE `sp_get_user_by_email` (
  IN `p_email` VARCHAR(150)
)
BEGIN
  DECLARE `v_email` VARCHAR(150);

  SET `v_email` = LOWER(TRIM(COALESCE(`p_email`, '')));

  SELECT
    `user_id`,
    `full_name` AS `name`,
    `username` AS `email`,
    `role`
  FROM `users`
  WHERE LOWER(`username`) = `v_email`
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_create_password_reset_token` (
  IN `p_user_id` INT,
  IN `p_token_hash` CHAR(64),
  IN `p_expires_at` DATETIME,
  IN `p_request_ip` VARCHAR(45),
  IN `p_user_agent` VARCHAR(255)
)
BEGIN
  CALL `sp_expire_old_password_reset_tokens`();

  UPDATE `password_reset_tokens`
  SET `status` = 'EXPIRED'
  WHERE `user_id` = `p_user_id`
    AND `status` = 'ACTIVE';

  INSERT INTO `password_reset_tokens`
    (`user_id`, `token_hash`, `expires_at`, `request_ip`, `user_agent`, `status`)
  VALUES
    (`p_user_id`, `p_token_hash`, `p_expires_at`, `p_request_ip`, LEFT(`p_user_agent`, 255), 'ACTIVE');

  SELECT
    `reset_id`,
    `user_id`,
    `expires_at`,
    `status`
  FROM `password_reset_tokens`
  WHERE `reset_id` = LAST_INSERT_ID();
END$$

CREATE PROCEDURE `sp_validate_password_reset_token` (
  IN `p_token_hash` CHAR(64)
)
BEGIN
  CALL `sp_expire_old_password_reset_tokens`();

  SELECT
    `prt`.`reset_id`,
    `prt`.`user_id`,
    `prt`.`expires_at`,
    `prt`.`status`,
    `u`.`full_name` AS `name`,
    `u`.`username` AS `email`,
    `u`.`role`
  FROM `password_reset_tokens` `prt`
  JOIN `users` `u` ON `u`.`user_id` = `prt`.`user_id`
  WHERE `prt`.`token_hash` = `p_token_hash`
    AND `prt`.`status` = 'ACTIVE'
    AND `prt`.`used_at` IS NULL
    AND `prt`.`expires_at` > NOW()
  LIMIT 1;
END$$

CREATE PROCEDURE `sp_update_user_password` (
  IN `p_user_id` INT,
  IN `p_password_hash` VARCHAR(255),
  IN `p_password_salt` VARCHAR(64)
)
BEGIN
  UPDATE `users`
  SET
    `password_hash` = `p_password_hash`,
    `password_salt` = `p_password_salt`
  WHERE `user_id` = `p_user_id`;

  SELECT ROW_COUNT() AS `affected_rows`;
END$$

CREATE PROCEDURE `sp_mark_password_reset_token_used` (
  IN `p_token_hash` CHAR(64)
)
BEGIN
  UPDATE `password_reset_tokens`
  SET
    `status` = 'USED',
    `used_at` = NOW()
  WHERE `token_hash` = `p_token_hash`
    AND `status` = 'ACTIVE'
    AND `used_at` IS NULL;

  SELECT ROW_COUNT() AS `affected_rows`;
END$$

DELIMITER ;
