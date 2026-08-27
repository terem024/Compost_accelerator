-- Select the target database first. Pause sign-in attempts during this repair.
-- Replaces only session validation, refresh and logout routines; no user data is changed.
SET @session_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';
DELIMITER $$
DROP PROCEDURE IF EXISTS `sp_validate_user_session`$$
CREATE PROCEDURE `sp_validate_user_session` (
  IN `p_session_token_hash` CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
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

DROP PROCEDURE IF EXISTS `sp_refresh_user_session`$$
CREATE PROCEDURE `sp_refresh_user_session` (
  IN `p_session_token_hash` CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
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

DROP PROCEDURE IF EXISTS `sp_logout_user_session`$$
CREATE PROCEDURE `sp_logout_user_session` (
  IN `p_session_token_hash` CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
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
DELIMITER ;
SET SESSION sql_mode = @session_previous_sql_mode;
