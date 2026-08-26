import { useState, useEffect } from 'react';
import { getThresholdSettings, saveThresholdSettings } from '../../services/api.js';
import { getThresholds as getLocalThresholds, saveThresholds as saveLocalThresholds } from '../../utils/thresholdCache.js';
import PasswordInput from '../PasswordInput.jsx';

const defaultThresholds = {
  moistureMin: 50,
  gasMax: 60,
  readingIntervalSeconds: 60,
  sprayDurationSeconds: 15,
  fanDurationSeconds: 5,
  sprayCooldownSeconds: 30,
  fanCooldownSeconds: 30,
};

function ThresholdSection() {
  const [thresholds, setThresholds] = useState(defaultThresholds);
  const [passwordPromptOpen, setPasswordPromptOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    async function loadThresholds() {
      try {
        const data = await getThresholdSettings();
        setThresholds({
          ...defaultThresholds,
          ...data,
        });
      } catch {
        setThresholds({
          ...defaultThresholds,
          ...getLocalThresholds(),
        });
      }
    }

    loadThresholds();
  }, []);

  const handleChange = (key, value) => {
    setThresholds((prev) => ({
      ...prev,
      [key]: value === '' ? '' : Number(value),
    }));
  };

  const handleSave = () => {
    setCurrentPassword('');
    setPasswordError('');
    setPasswordPromptOpen(true);
  };

  const confirmSave = async (event) => {
    event.preventDefault();
    setPasswordError('');

    if (!currentPassword) {
      setPasswordError('Enter your current password to save threshold settings.');
      return;
    }

    setSaving(true);

    try {
      const saved = await saveThresholdSettings({
        ...thresholds,
        currentPassword,
      });
      const nextThresholds = {
        ...defaultThresholds,
        ...saved,
      };

      setThresholds(nextThresholds);
      saveLocalThresholds(nextThresholds);
      window.showToast('Threshold settings updated successfully', 'success');
      setCurrentPassword('');
      setPasswordPromptOpen(false);
    } catch (error) {
      window.showToast(error.message || 'Unable to save threshold settings.', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
      <div className="info-box settings-full-box">
      <h4>Threshold Setting</h4>

      <div className="threshold-form">
        <div className="threshold-grid">
          <label>
            Moisture Min (%)
            <input
              type="number"
              value={thresholds.moistureMin}
              onChange={(e) => handleChange('moistureMin', e.target.value)}
              placeholder="50"
            />
          </label>

          <label>
            Gas Max (%)
            <input
              type="number"
              value={thresholds.gasMax}
              onChange={(e) => handleChange('gasMax', e.target.value)}
              placeholder="60"
              min="0"
              max="100"
            />
          </label>

          <label>
            Spray Cooldown (seconds)
            <input
              type="number"
              value={thresholds.sprayCooldownSeconds}
              onChange={(e) => handleChange('sprayCooldownSeconds', e.target.value)}
              placeholder="30"
            />
          </label>

          <label>
            Fan Cooldown (seconds)
            <input
              type="number"
              value={thresholds.fanCooldownSeconds}
              onChange={(e) => handleChange('fanCooldownSeconds', e.target.value)}
              placeholder="30"
            />
          </label>

        </div>

        <button className="save-button" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>

      {passwordPromptOpen && (
        <div className="threshold-password-overlay" role="dialog" aria-modal="true" aria-labelledby="threshold-password-title">
          <form className="threshold-password-card" onSubmit={confirmSave}>
            <h3 id="threshold-password-title">Confirm Threshold Change</h3>
            <p>Enter your current account password before saving preset threshold values.</p>
            {passwordError && <p className="form-message error">{passwordError}</p>}

            <label>
              Current password
              <PasswordInput
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                autoFocus
              />
            </label>

            <div className="threshold-password-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  setPasswordPromptOpen(false);
                  setCurrentPassword('');
                  setPasswordError('');
                }}
                disabled={saving}
              >
                Cancel
              </button>
              <button type="submit" className="primary-button" disabled={saving}>
                {saving ? 'Verifying...' : 'Confirm Save'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

export default ThresholdSection;
