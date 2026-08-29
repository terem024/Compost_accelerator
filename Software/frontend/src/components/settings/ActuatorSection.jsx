import { useState, useEffect } from 'react';
import { getActuatorStatus, getThresholdSettings } from '../../services/api.js';
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
  const [thresholds, setThresholds] = useState(null);
  const [loading, setLoading] = useState(true);
  const [statusError, setStatusError] = useState('');

  useEffect(() => {
    let cancelled = false;

    async function loadInitialData() {
      try {
        const [status, settings] = await Promise.all([
          getActuatorStatus(),
          getThresholdSettings(),
        ]);
        if (cancelled) return;
        setActuatorStatus(status);
        setThresholds(settings);
        setStatusError('');
      } catch (error) {
        if (cancelled) return;
        setStatusError(error.message || 'Unable to load actuator status.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    async function refreshStatus() {
      try {
        const status = await getActuatorStatus();
        if (cancelled) return;
        setActuatorStatus(status);
        setStatusError('');
      } catch (error) {
        if (cancelled) return;
        setStatusError(error.message || 'Unable to refresh actuator status.');
      }
    }

    loadInitialData();
    const interval = window.setInterval(refreshStatus, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  if (loading) {
    return (
      <div className="info-box settings-full-box">
        <h4>Batch & Actuator Controls</h4>
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
        <h4>Actuator Status</h4>
        {statusError && <p className="form-message error">{statusError}</p>}
        {thresholds && (
          <p>
            Current timing: water spray {thresholds.sprayDurationSeconds}s with a {thresholds.sprayCooldownSeconds}s cooldown;
            {' '}fan {thresholds.fanDurationSeconds}s with a {thresholds.fanCooldownSeconds}s cooldown.
          </p>
        )}

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
