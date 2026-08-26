-- Require the currently logged-in user's password before threshold changes.
-- Apply to the existing compost_system database.

DROP PROCEDURE IF EXISTS `sp_verify_user_password`;

DELIMITER $$

CREATE PROCEDURE `sp_verify_user_password` (
  IN `p_user_id` INT,
  IN `p_password` VARCHAR(255)
)
BEGIN
  SELECT
    CASE
      WHEN EXISTS (
        SELECT 1
        FROM `users`
        WHERE `user_id` = `p_user_id`
          AND `password_salt` IS NOT NULL
          AND `password_hash` = SHA2(CONCAT(`password_salt`, `p_password`), 256)
      ) THEN 1
      ELSE 0
    END AS `password_valid`;
END$$

DELIMITER ;
