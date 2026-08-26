import { useState } from 'react';
import { forgotPassword } from '../../services/api.js';

function MeSection({ user, onLogout }) {
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [sendingReset, setSendingReset] = useState(false);

  const handlePasswordReset = async () => {
    setMessage('');
    setError('');

    if (!user?.email) {
      setError('No email address is linked to this account.');
      return;
    }

    setSendingReset(true);

    try {
      const response = await forgotPassword(user.email);
      setMessage(response.message || 'If the email is registered, a password reset link has been sent.');
    } catch (err) {
      setError(err.message || 'Unable to send password reset email.');
    } finally {
      setSendingReset(false);
    }
  };

  return (
    <div className="info-box settings-full-box settings-me-box">
      <div className="me-content">
        <div className="me-title-row">
          <h4>Account Information</h4>
        </div>
        <div className="user-details">
          <div className="detail-row">
            <span className="label">Name:</span>
            <span className="value">{user?.name || 'N/A'}</span>
          </div>
          <div className="detail-row">
            <span className="label">Email:</span>
            <span className="value">{user?.email || 'N/A'}</span>
          </div>
          <div className="detail-row">
            <span className="label">Role:</span>
            <span className="value">{user?.role || 'User'}</span>
          </div>
          <div className="detail-row">
            <span className="label">Member Since:</span>
            <span className="value">{user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'N/A'}</span>
          </div>
        </div>

        <div className="account-security-panel">
          <h4>Password Reset</h4>
          {message && <p className="form-message success">{message}</p>}
          {error && <p className="form-message error">{error}</p>}
          <div className="account-action-row">
            <button
              type="button"
              className="primary-button account-action-button"
              onClick={handlePasswordReset}
              disabled={sendingReset}
            >
              {sendingReset ? 'Sending...' : 'Send Password Reset Email'}
            </button>

            <button className="logout-button" onClick={onLogout}>
              Logout
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default MeSection;
