-- Apply this to an existing compost_system database so the dashboard latest
-- reading endpoint can read through a stored procedure.

DROP PROCEDURE IF EXISTS `sp_get_latest_sensor_reading`;

DELIMITER $$

CREATE PROCEDURE `sp_get_latest_sensor_reading` () BEGIN
    DECLARE v_batch_id INT DEFAULT NULL;

    SET v_batch_id = (
        SELECT batch_id
        FROM compost_batches
        WHERE status = 'ACTIVE'
        ORDER BY start_date DESC, batch_id DESC
        LIMIT 1
    );

    IF v_batch_id IS NULL THEN
        SELECT
            reading_id,
            batch_id,
            moisture_level,
            gas_level,
            temperature_c,
            humidity_level,
            moisture_status,
            gas_status,
            temperature_status,
            humidity_status,
            created_at
        FROM sensor_readings
        ORDER BY created_at DESC, reading_id DESC
        LIMIT 1;
    ELSE
        SELECT
            reading_id,
            batch_id,
            moisture_level,
            gas_level,
            temperature_c,
            humidity_level,
            moisture_status,
            gas_status,
            temperature_status,
            humidity_status,
            created_at
        FROM sensor_readings
        WHERE batch_id = v_batch_id
        ORDER BY created_at DESC, reading_id DESC
        LIMIT 1;
    END IF;
END$$

DELIMITER ;
