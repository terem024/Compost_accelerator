-- Apply this to an existing compost_system database that was imported before
-- the sensor range update.

ALTER TABLE `sensor_readings`
  MODIFY `gas_status` enum('LOW','NORMAL','HIGH') NOT NULL;

UPDATE `threshold_settings`
SET
  `moisture_min` = 50.00,
  `gas_max` = 60.00
WHERE `moisture_min` < 50.00
   OR `gas_max` > 100.00;

UPDATE `sensor_readings`
SET
  `moisture_status` = CASE
    WHEN `moisture_level` < 50.00 THEN 'LOW'
    WHEN `moisture_level` > 70.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END,
  `gas_status` = CASE
    WHEN `gas_level` < 40.00 THEN 'LOW'
    WHEN `gas_level` > 60.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END,
  `temperature_status` = CASE
    WHEN `temperature_c` < 30.00 THEN 'LOW'
    WHEN `temperature_c` > 50.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END,
  `humidity_status` = CASE
    WHEN `humidity_level` < 40.00 THEN 'LOW'
    WHEN `humidity_level` > 70.00 THEN 'HIGH'
    ELSE 'NORMAL'
  END;
