-- Select the target database before running this script.
-- Replaces only the login procedure; existing users and passwords are unchanged.
-- Avoid login attempts while the procedure is being replaced.
SET @login_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

DELIMITER $$
DROP PROCEDURE IF EXISTS `sp_login_user`$$
CREATE PROCEDURE `sp_login_user` (
  IN `p_username_or_email` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN `p_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
)
BEGIN
  DECLARE `v_identifier` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

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
DELIMITER ;

SET SESSION sql_mode = @login_previous_sql_mode;

SELECT PARAMETER_NAME, COLLATION_NAME
FROM information_schema.PARAMETERS
WHERE SPECIFIC_SCHEMA = DATABASE()
  AND SPECIFIC_NAME = 'sp_login_user'
ORDER BY ORDINAL_POSITION;
