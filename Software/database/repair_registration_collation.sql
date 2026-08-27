-- Run with the intended database selected (USE railway on the hosted system).
-- Matches the existing users columns without converting tables or changing rows.
-- Recreates only sp_register_user; run while registration is not being used.
-- Preserve the SQL mode reported by SHOW CREATE PROCEDURE on the hosted system.
SET @registration_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

DELIMITER $$

DROP PROCEDURE IF EXISTS `sp_register_user`$$
CREATE PROCEDURE `sp_register_user` (
  IN `p_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_email` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
)
BEGIN
  DECLARE `v_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE `v_username` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE `v_salt` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
  DECLARE `v_password_hash` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

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

DELIMITER ;

SET SESSION sql_mode = @registration_previous_sql_mode;

SELECT PARAMETER_NAME, COLLATION_NAME
FROM information_schema.PARAMETERS
WHERE SPECIFIC_SCHEMA = DATABASE()
  AND SPECIFIC_NAME = 'sp_register_user'
ORDER BY ORDINAL_POSITION;
