import { useEffect, useMemo, useState } from 'react';
import {
  createCompostBatch,
  getActiveCompostBatch,
  getCompostBatches,
  setActiveCompostBatch,
  updateCompostBatch,
  updateCompostBatchStatus,
} from '../../services/api.js';
import PasswordInput from '../PasswordInput.jsx';

function todayString() {
  return new Date().toISOString().slice(0, 10);
}

function emptyForm() {
  return {
    batchName: '',
    primaryMaterial: '',
    materialDescription: '',
    fillLevel: 'HALF',
    startDate: todayString(),
    binLocation: '',
    notes: '',
  };
}

function formFromBatch(batch) {
  if (!batch) return emptyForm();

  return {
    batchName: batch.batchName || '',
    primaryMaterial: batch.primaryMaterial || '',
    materialDescription: batch.materialDescription || '',
    fillLevel: batch.fillLevel || 'HALF',
    startDate: batch.startDate || todayString(),
    binLocation: batch.binLocation || '',
    notes: batch.notes || '',
  };
}

function formatDate(value) {
  if (!value) return 'Not set';
  return value;
}

function formatFillLevel(value) {
  if (value === 'FULL') return 'Full';
  if (value === 'ONE_THIRD') return 'Low (one-third)';
  return 'Half full';
}

function ActiveCompostBatchSection() {
  const [activeBatch, setActiveBatch] = useState(null);
  const [batches, setBatches] = useState([]);
  const [selectedBatchId, setSelectedBatchId] = useState(null);
  const [form, setForm] = useState(() => emptyForm());
  const [mode, setMode] = useState('view');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [passwordPromptOpen, setPasswordPromptOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');

  const selectedBatch = useMemo(
    () => batches.find((batch) => batch.batchId === selectedBatchId) || activeBatch,
    [activeBatch, batches, selectedBatchId]
  );

  const isCreating = mode === 'create';
  const isEditing = mode === 'edit';
  const formEditable = isCreating || isEditing;

  async function loadBatches() {
    setLoading(true);
    setError('');

    try {
      const [batchList, active] = await Promise.all([
        getCompostBatches(),
        getActiveCompostBatch().catch(() => null),
      ]);

      const list = Array.isArray(batchList) ? batchList : [];
      const currentActive = active || list.find((batch) => batch.status === 'ACTIVE') || null;

      setBatches(list);
      setActiveBatch(currentActive);
      setSelectedBatchId(currentActive?.batchId || list[0]?.batchId || null);
      setForm(formFromBatch(currentActive || list[0]));
    } catch (err) {
      setError(err.message || 'Failed to load compost batches.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadBatches();
  }, []);

  function clearMessages() {
    setMessage('');
    setError('');
    setPasswordError('');
  }

  function updateField(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }));
  }

  function selectBatch(batch) {
    if (mode !== 'view') {
      return;
    }

    setSelectedBatchId(batch.batchId);
    setForm(formFromBatch(batch));
    clearMessages();
  }

  function handleStartCreate() {
    setMode('create');
    setSelectedBatchId(null);
    setForm(emptyForm());
    clearMessages();
  }

  function handleStartEdit() {
    if (!activeBatch?.batchId) {
      setError('No active compost batch is available to edit.');
      return;
    }

    setMode('edit');
    setSelectedBatchId(activeBatch.batchId);
    setForm(formFromBatch(activeBatch));
    clearMessages();
  }

  function handleCancelEdit() {
    setMode('view');
    setCurrentPassword('');
    setPasswordPromptOpen(false);
    setForm(formFromBatch(selectedBatch || activeBatch));
    clearMessages();
  }

  async function handleCreateBatch(event) {
    event.preventDefault();
    setSaving(true);
    clearMessages();

    try {
      const created = await createCompostBatch(form);
      setMessage(`${created.batchCode} is now active.`);
      setMode('view');
      await loadBatches();
    } catch (err) {
      setError(err.message || 'Failed to create compost batch.');
    } finally {
      setSaving(false);
    }
  }

  function handleRequestUpdate(event) {
    event.preventDefault();

    if (!activeBatch?.batchId || selectedBatchId !== activeBatch.batchId) {
      setError('Only the current active compost batch can be edited here.');
      return;
    }

    clearMessages();
    setCurrentPassword('');
    setPasswordPromptOpen(true);
  }

  async function confirmUpdateBatch(event) {
    event.preventDefault();
    setPasswordError('');

    if (!currentPassword) {
      setPasswordError('Enter your current password to save compost batch changes.');
      return;
    }

    setSaving(true);

    try {
      const updated = await updateCompostBatch(activeBatch.batchId, {
        ...form,
        currentPassword,
      });
      setMessage(`${updated.batchCode} was updated.`);
      setMode('view');
      setCurrentPassword('');
      setPasswordPromptOpen(false);
      await loadBatches();
    } catch (err) {
      setPasswordError(err.message || 'Failed to update compost batch.');
    } finally {
      setSaving(false);
    }
  }

  async function handleSetActive(batchId) {
    setSaving(true);
    clearMessages();

    try {
      const updated = await setActiveCompostBatch(batchId);
      setMessage(`${updated.batchCode} is now active.`);
      await loadBatches();
    } catch (err) {
      setError(err.message || 'Failed to set active batch.');
    } finally {
      setSaving(false);
    }
  }

  async function handleStatus(status) {
    if (!selectedBatch?.batchId) {
      setError('Select a compost batch first.');
      return;
    }

    setSaving(true);
    clearMessages();

    try {
      const updated = await updateCompostBatchStatus(selectedBatch.batchId, status);
      setMessage(`${updated.batchCode} status changed to ${updated.status}.`);
      await loadBatches();
    } catch (err) {
      setError(err.message || 'Failed to update batch status.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="info-box settings-full-box">
        <h4>Active Compost Batch</h4>
        <p>Loading compost batches...</p>
      </div>
    );
  }

  return (
    <div className="info-box active-batch-section">
      <div className="active-batch-header">
        <div>
          <h4>Active Compost Batch</h4>
          <p className="note-text">
            {activeBatch
              ? `${activeBatch.batchCode} - ${activeBatch.batchName}`
              : 'No active compost batch'}
          </p>
        </div>
        <div className="active-batch-header-actions">
          <span className={`batch-status-pill ${activeBatch ? 'active' : 'inactive'}`}>
            {activeBatch?.status || 'NONE'}
          </span>
          {mode === 'view' && activeBatch && (
            <button
              type="button"
              className="secondary-button"
              onClick={handleStartEdit}
              disabled={saving}
            >
              Edit Active Batch
            </button>
          )}
        </div>
      </div>

      {message && <p className="form-message success">{message}</p>}
      {error && <p className="form-message error">{error}</p>}

      {activeBatch && (
        <div className="active-batch-summary">
          <div>
            <span>Primary material</span>
            <strong>{activeBatch.primaryMaterial}</strong>
          </div>
          <div>
            <span>Start date</span>
            <strong>{formatDate(activeBatch.startDate)}</strong>
          </div>
          <div>
            <span>Fill level</span>
            <strong>{formatFillLevel(activeBatch.fillLevel)}</strong>
          </div>
          <div>
            <span>AI estimated ready</span>
            <strong>{formatDate(activeBatch.latestPredictedReadyDate)}</strong>
          </div>
          <div>
            <span>Bin location</span>
            <strong>{activeBatch.binLocation || 'Not set'}</strong>
          </div>
        </div>
      )}

      {formEditable && (
        <form className="batch-form" onSubmit={isCreating ? handleCreateBatch : handleRequestUpdate}>
          <div className="batch-form-header">
            <h4>{isCreating ? 'Create Compost Batch' : 'Edit Active Compost Batch'}</h4>
            <p>Update the compost batch information below.</p>
          </div>

          <div className="batch-form-grid">
            <label>
              Batch name
              <input
                type="text"
                value={form.batchName}
                disabled={saving}
                onChange={(event) => updateField('batchName', event.target.value)}
              />
            </label>

            <label>
              Primary material
              <input
                type="text"
                value={form.primaryMaterial}
                disabled={saving}
                onChange={(event) => updateField('primaryMaterial', event.target.value)}
              />
            </label>

            <label>
              Start date
              <input
                type="date"
                value={form.startDate}
                disabled={saving}
                onChange={(event) => updateField('startDate', event.target.value)}
              />
            </label>

            <label>
              Compost fill level
              <select
                value={form.fillLevel}
                disabled={saving}
                onChange={(event) => updateField('fillLevel', event.target.value)}
              >
                <option value="ONE_THIRD">Low (one-third)</option>
                <option value="HALF">Half full</option>
                <option value="FULL">Full</option>
              </select>
            </label>

            <label>
              Bin location
              <input
                type="text"
                value={form.binLocation}
                disabled={saving}
                onChange={(event) => updateField('binLocation', event.target.value)}
              />
            </label>

            <label>
              Material description
              <textarea
                value={form.materialDescription}
                disabled={saving}
                onChange={(event) => updateField('materialDescription', event.target.value)}
              />
            </label>

            <label className="batch-notes-field">
              Notes
              <textarea
                value={form.notes}
                disabled={saving}
                onChange={(event) => updateField('notes', event.target.value)}
              />
            </label>
          </div>

          <div className="batch-action-row">
            {isCreating && (
              <>
                <button type="submit" className="primary-button" disabled={saving}>
                  {saving ? 'Saving...' : 'Create Batch'}
                </button>
                <button type="button" className="secondary-button" onClick={handleCancelEdit} disabled={saving}>
                  Cancel
                </button>
              </>
            )}

            {isEditing && (
              <>
                <button type="submit" className="primary-button" disabled={saving}>
                  {saving ? 'Saving...' : 'Save Changes'}
                </button>
                <button type="button" className="secondary-button" onClick={handleCancelEdit} disabled={saving}>
                  Cancel
                </button>
              </>
            )}
          </div>
        </form>
      )}

      {mode === 'view' && (
        <div className="batch-action-row">
          <button type="button" className="primary-button" onClick={handleStartCreate} disabled={saving}>
            Create Batch
          </button>
          <button type="button" className="secondary-button" onClick={() => handleStatus('READY')} disabled={saving}>
            Mark READY
          </button>
          <button type="button" className="secondary-button" onClick={() => handleStatus('COMPLETED')} disabled={saving}>
            Mark COMPLETED
          </button>
          <button type="button" className="secondary-button" onClick={() => handleStatus('CANCELLED')} disabled={saving}>
            Mark CANCELLED
          </button>
        </div>
      )}

      {batches.length > 0 && (
        <div className="batch-list">
          {batches.map((batch) => (
            <div
              key={batch.batchId}
              className={`batch-list-row ${batch.batchId === selectedBatch?.batchId ? 'selected' : ''}`}
            >
              <button
                type="button"
                className="batch-select-button"
                onClick={() => selectBatch(batch)}
                disabled={mode !== 'view'}
              >
                <strong>{batch.batchCode}</strong>
                <span>{batch.batchName} - {formatFillLevel(batch.fillLevel)}</span>
              </button>
              <span className={`batch-status-pill ${batch.status === 'ACTIVE' ? 'active' : 'inactive'}`}>
                {batch.status}
              </span>
              {batch.status !== 'ACTIVE' && batch.status !== 'COMPLETED' && batch.status !== 'CANCELLED' && (
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => handleSetActive(batch.batchId)}
                  disabled={saving || mode !== 'view'}
                >
                  Set Active
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {passwordPromptOpen && (
        <div className="threshold-password-overlay" role="dialog" aria-modal="true" aria-labelledby="batch-password-title">
          <form className="threshold-password-card" onSubmit={confirmUpdateBatch}>
            <h3 id="batch-password-title">Confirm Batch Change</h3>
            <p>Enter your current account password before saving changes to the active compost batch.</p>
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

export default ActiveCompostBatchSection;
