function SensorConnectionAlert({ status }) {
  if (status?.connectionStatus !== 'DISCONNECTED') return null;

  const lastReading = status.lastReadingAt
    ? new Date(status.lastReadingAt).toLocaleString()
    : 'No sensor reading received';

  return (
    <div className="sensor-connection-alert-backdrop" role="alertdialog" aria-modal="true">
      <div className="sensor-connection-alert" aria-live="assertive">
        <div className="sensor-connection-alert-icon" aria-hidden="true">!</div>
        <h2>ESP32 Sensor Connection Lost</h2>
        <p>
          No new sensor reading has been received for more than three minutes.
          Sensor status is currently N/A.
        </p>
        <div className="sensor-connection-alert-time">
          Last reading: {lastReading}
        </div>
        <span>This notice will clear automatically when a new reading is received.</span>
      </div>
    </div>
  );
}

export default SensorConnectionAlert;
