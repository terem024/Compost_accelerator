import { useState, useEffect } from 'react';
import { getActuatorStatus } from '../../services/api.js';
import ActiveCompostBatchSection from './ActiveCompostBatchSection.jsx';

function formatDateTime(value) {
  if (!value) return 'No activity yet';
  return new Date(value).toLocaleString();
}

function getRuntime(status, actuatorType) {
  return status?.actuators?.find((actuator) => actuator.actuatorType === actuatorType);
}

function getStatusLabel(runtime, active) {
  if (active) return 'Running';

  const cooldownUntil = runtime?.cooldownUntil ? new Date(runtime.cooldownUntil) : null;
  if (cooldownUntil && cooldownUntil > new Date()) {
    return 'Cooldown';
  }

  return 'Idle';
}

function ActuatorSection() {
  const [actuatorStatus, setActuatorStatus] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      try {
        const status = await getActuatorStatus();
        setActuatorStatus(status);
      } catch {
        setActuatorStatus(null);
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, []);

  if (loading) {
    return (
      <div className="info-box settings-full-box">
        <h4>Actuator Controls</h4>
        <p>Loading latest actuator status...</p>
      </div>
    );
  }

  const fanRuntime = getRuntime(actuatorStatus, 'FAN');
  const sprayRuntime = getRuntime(actuatorStatus, 'WATER_SPRAY');
  const latestActivity = actuatorStatus?.latestActivity;

  return (
    <div className="controls-section-stack">
      <ActiveCompostBatchSection />

      <div className="info-box settings-full-box">
        <h4>Actuator Controls</h4>
        <p>Fan and water spray activations use a fixed 10-second pulse while hardware output is being calibrated.</p>

        <div className="actuator-status-grid">
          <div className="actuator-card">
            <div className="actuator-title">Fan</div>
            <div className={`actuator-badge ${actuatorStatus?.fanActive ? 'active' : 'inactive'}`}>
              {getStatusLabel(fanRuntime, actuatorStatus?.fanActive)}
            </div>
            <p>Last activated: {formatDateTime(fanRuntime?.lastActivatedAt)}</p>
            <p>Cooldown until: {formatDateTime(fanRuntime?.cooldownUntil)}</p>
          </div>

          <div className="actuator-card">
            <div className="actuator-title">Water Spray</div>
            <div className={`actuator-badge ${actuatorStatus?.waterPumpActive ? 'active' : 'inactive'}`}>
              {getStatusLabel(sprayRuntime, actuatorStatus?.waterPumpActive)}
            </div>
            <p>Last activated: {formatDateTime(sprayRuntime?.lastActivatedAt)}</p>
            <p>Cooldown until: {formatDateTime(sprayRuntime?.cooldownUntil)}</p>
          </div>
        </div>

        {latestActivity && (
          <div className="latest-activity-inline">
            Latest activity: {latestActivity.actuatorType} from {latestActivity.triggerSource}, {latestActivity.durationSeconds}s pulse.
          </div>
        )}
      </div>
    </div>
  );
}

export default ActuatorSection;
