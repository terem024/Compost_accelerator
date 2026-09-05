-- Run once against the Railway database before deploying partial sensor uploads.
-- Existing rows are preserved. Re-running this script is safe.

ALTER TABLE `sensor_readings`
  MODIFY `moisture_level` DECIMAL(5,2) NULL,
  MODIFY `gas_level` DECIMAL(8,2) NULL,
  MODIFY `temperature_c` DECIMAL(5,2) NULL,
  MODIFY `humidity_level` DECIMAL(5,2) NULL,
  MODIFY `moisture_status` ENUM('LOW','NORMAL','HIGH') NULL,
  MODIFY `gas_status` ENUM('LOW','NORMAL','HIGH') NULL,
  MODIFY `temperature_status` ENUM('LOW','NORMAL','HIGH') NULL,
  MODIFY `humidity_status` ENUM('LOW','NORMAL','HIGH') NULL;
