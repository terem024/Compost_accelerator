import { Link } from 'react-router-dom';

function SensorConnectionAlert({ status, noActiveBatch = false }) {
  if (noActiveBatch) {
    return (
      <div className="system-alert batch-alert" role="alert" aria-live="assertive">
        <div className="system-alert-icon" aria-hidden="true">!</div>
        <div className="system-alert-copy">
          <strong>No active compost batch</strong>
          <span>Sensor readings cannot be saved until a batch is created or activated.</span>
        </div>
        <Link className="system-alert-action" to="/settings?section=controls">
          Open batch controls
        </Link>
      </div>
    );
  }

  if (status?.connectionStatus !== 'DISCONNECTED') return null;

  const lastReading = status.lastReadingAt
    ? new Date(status.lastReadingAt).toLocaleString()
    : 'No sensor reading received';

  return (
    <div className="system-alert connection-alert" role="alert" aria-live="assertive">
      <div className="system-alert-icon" aria-hidden="true">!</div>
      <div className="system-alert-copy">
        <strong>ESP32 connection lost</strong>
        <span>No reading received for more than three minutes. Last reading: {lastReading}</span>
      </div>
    </div>
  );
}

export default SensorConnectionAlert;
