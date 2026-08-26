-- Apply this to an existing compost_system database to support backend actuator
-- logging through a stored procedure.

DROP PROCEDURE IF EXISTS `sp_insert_actuator_log`;

DELIMITER $$

CREATE PROCEDURE `sp_insert_actuator_log` (
    IN `p_batch_id` INT,
    IN `p_actuator_type` VARCHAR(20),
    IN `p_status` VARCHAR(10),
    IN `p_trigger_source` VARCHAR(20),
    IN `p_related_reading_id` INT,
    IN `p_triggered_by_user_id` INT,
    IN `p_reason` VARCHAR(255)
) BEGIN
    INSERT INTO actuator_logs (
        batch_id,
        actuator_type,
        status,
        trigger_source,
        related_reading_id,
        triggered_by_user_id,
        reason
    )
    VALUES (
        p_batch_id,
        p_actuator_type,
        p_status,
        p_trigger_source,
        p_related_reading_id,
        p_triggered_by_user_id,
        p_reason
    );
END$$

DELIMITER ;

UPDATE sensor_readings
SET batch_id = (
    SELECT batch_id
    FROM compost_batches
    WHERE status = 'ACTIVE'
    ORDER BY start_date DESC, batch_id DESC
    LIMIT 1
)
WHERE batch_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM compost_batches
    WHERE status = 'ACTIVE'
  );
