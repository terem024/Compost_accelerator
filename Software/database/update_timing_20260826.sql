USE `compost_system`;

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
SELECT
  `moisture_min`,
  `gas_max`,
  60,
  15,
  5,
  30,
  30,
  `updated_by`
FROM `threshold_settings`
ORDER BY `setting_id` DESC
LIMIT 1;
