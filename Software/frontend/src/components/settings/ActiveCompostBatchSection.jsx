import { useEffect, useMemo, useRef, useState } from 'react';
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
    notes: '',
  };
}

function formFromBatch(batch) {
  if (!batch) return emptyForm();

  return {
    batchName: batch.batchName || '',
    primaryMaterial: batch.primaryMaterial || '',
    materialDescription: batch.materialDescription || '',
    fillLevel: batch.fillLevel === 'FULL' ? 'FULL' : 'HALF',
    startDate: batch.startDate || todayString(),
    notes: batch.notes || '',
  };
}

function formatDate(value) {
  if (!value) return 'Not set';
  return value;
}

function formatFillLevel(value) {
  if (value === 'FULL') return '10 kg';
  if (value === 'HALF') return '5 kg';
  return 'Legacy batch weight';
}

function ActiveCompostBatchSection() {
  const [activeBatch, setActiveBatch] = useState(null);
  const [batches, setBatches] = useState([]);
  const [selectedBatchId, setSelectedBatchId] = useState(null);
  const [form, setForm] = useState(() => emptyForm());
  const [mode, setMode] = useState('view');
  const [loading, setLoading] = useState(true);
  const [batchesLoaded, setBatchesLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [passwordPromptOpen, setPasswordPromptOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [pendingStatusChange, setPendingStatusChange] = useState(null);
  const [statusConfirmation, setStatusConfirmation] = useState('');
  const [statusError, setStatusError] = useState('');
  const statusTriggerRef = useRef(null);

  const selectedBatch = useMemo(
    () => batches.find((batch) => batch.batchId === selectedBatchId) || activeBatch,
    [activeBatch, batches, selectedBatchId]
  );

  const isCreating = mode === 'create';
  const isEditing = mode === 'edit';
  const formEditable = isCreating || isEditing;
  const selectedBatchIsFinal = selectedBatch?.status === 'COMPLETED'
    || selectedBatch?.status === 'CANCELLED';
  const canUpdateSelectedStatus = Boolean(selectedBatch?.batchId) && !selectedBatchIsFinal;

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
      setBatchesLoaded(true);
    } catch (err) {
      setBatchesLoaded(false);
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
    setStatusError('');
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
    if (!batchesLoaded || activeBatch) return;
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
    if (!batchesLoaded || activeBatch) {
      setError('An ongoing batch already exists, or batch status could not be verified. Refresh the batch list before creating another batch.');
      return;
    }
    setSaving(true);
    clearMessages();

    try {
      const created = await createCompostBatch(form);
      setMessage(`${created.batchCode} is now active.`);
      setMode('view');
      await loadBatches();
    } catch (err) {
      setError(err.message || 'Failed to create compost batch.');
      if (err.status === 409) {
        setMode('view');
        await loadBatches();
        setError(err.message);
      }
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
    if (!batchesLoaded || (activeBatch && activeBatch.batchId !== batchId)) {
      setError('Finish or cancel the ongoing batch before activating another batch.');
      return;
    }
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

  function handleStatus(status, trigger) {
    if (!canUpdateSelectedStatus || saving) {
      setError('Select a compost batch first.');
      return;
    }

    clearMessages();
    statusTriggerRef.current = trigger;
    setStatusConfirmation('');
    setStatusError('');
    setPendingStatusChange({ batchId: selectedBatch.batchId, batchCode: selectedBatch.batchCode, status });
  }

  function closeStatusConfirmation() {
    if (saving) return;
    setPendingStatusChange(null);
    setStatusConfirmation('');
    setStatusError('');
    statusTriggerRef.current?.focus();
  }

  function handleConfirmationKeyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeStatusConfirmation();
    }
    if (event.key === 'Tab') {
      const controls = [...event.currentTarget.querySelectorAll('input:not(:disabled), button:not(:disabled)')];
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    }
  }

  async function confirmStatusChange(event) {
    event.preventDefault();
    if (!pendingStatusChange || statusConfirmation !== 'Confirm' || saving) return;

    setSaving(true);
    setStatusError('');

    try {
      const updated = await updateCompostBatchStatus(pendingStatusChange.batchId, pendingStatusChange.status);
      setMessage(`${updated.batchCode} status changed to ${updated.status}.`);
      setPendingStatusChange(null);
      setStatusConfirmation('');
      await loadBatches();
    } catch (err) {
      setStatusError(err.message || 'Failed to update batch status.');
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
            <span>Batch weight</span>
            <strong>{formatFillLevel(activeBatch.fillLevel)}</strong>
          </div>
          <div>
            <span>AI estimated ready</span>
            <strong>{activeBatch.latestPredictedReadyDate || 'Not available'}</strong>
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
              Compost batch weight
              <select
                value={form.fillLevel}
                disabled={saving}
                onChange={(event) => updateField('fillLevel', event.target.value)}
              >
                <option value="HALF">5 kg</option>
                <option value="FULL">10 kg</option>
              </select>
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
          <button type="button" className="primary-button" onClick={handleStartCreate}
            aria-describedby={activeBatch ? 'ongoing-batch-guard' : undefined}
            disabled={saving || loading || !batchesLoaded || Boolean(activeBatch)}>
            Create Batch
          </button>
          <button type="button" className="secondary-button" onClick={(event) => handleStatus('READY', event.currentTarget)} disabled={saving || !canUpdateSelectedStatus}>
            Mark READY
          </button>
          <button type="button" className="secondary-button" onClick={(event) => handleStatus('COMPLETED', event.currentTarget)} disabled={saving || !canUpdateSelectedStatus}>
            Mark COMPLETED
          </button>
          <button type="button" className="secondary-button" onClick={(event) => handleStatus('CANCELLED', event.currentTarget)} disabled={saving || !canUpdateSelectedStatus}>
            Mark CANCELLED
          </button>
        </div>
      )}
      {activeBatch && <p className="batch-creation-guard" id="ongoing-batch-guard" role="note">
        <strong>{activeBatch.batchCode || activeBatch.batchName} is ongoing.</strong>{' '}
        Finish or cancel this batch before creating or activating another batch.
      </p>}

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
                  disabled={saving || mode !== 'view' || !batchesLoaded || Boolean(activeBatch)}
                >
                  Set Active
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {pendingStatusChange && (
        <div className="threshold-password-overlay" role="dialog" aria-modal="true" aria-labelledby="batch-status-title" aria-describedby="batch-status-description" onKeyDown={handleConfirmationKeyDown}>
          <form className="threshold-password-card" onSubmit={confirmStatusChange}>
            <h3 id="batch-status-title">Confirm Batch Status</h3>
            <p id="batch-status-description">Mark {pendingStatusChange.batchCode} as {pendingStatusChange.status}?</p>
            {(pendingStatusChange.status === 'COMPLETED' || pendingStatusChange.status === 'CANCELLED') && (
              <p>This batch cannot be reactivated through the app after this change.</p>
            )}
            {statusError && <p className="form-message error" role="alert">{statusError}</p>}
            <label htmlFor="batch-status-confirmation">Type Confirm to proceed</label>
            <input
              id="batch-status-confirmation"
              type="text"
              value={statusConfirmation}
              onChange={(event) => setStatusConfirmation(event.target.value)}
              autoComplete="off"
              spellCheck={false}
              readOnly={saving}
              autoFocus
            />
            <div className="threshold-password-actions">
              <button type="button" className="secondary-button" onClick={closeStatusConfirmation} disabled={saving}>Go Back</button>
              <button type="submit" className="primary-button" disabled={saving || statusConfirmation !== 'Confirm'}>
                {saving ? 'Saving...' : `Mark ${pendingStatusChange.status}`}
              </button>
            </div>
          </form>
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
