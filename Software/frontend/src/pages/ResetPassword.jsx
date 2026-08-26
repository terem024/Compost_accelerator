import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../services/api.js';
import { sanitizeInput } from '../utils/sanitize.js';
import NotificationModal from '../components/NotificationModal.jsx';
import PasswordInput from '../components/PasswordInput.jsx';

const authIntro = {
  title: 'Build Your Skills With Compost Intelligence',
  description:
    'Monitor compost health and stay on top of environmental conditions with live sensor history, alerts, and actuator response simulation.',
};

function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';

  const [form, setForm] = useState({
    newPassword: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notification, setNotification] = useState({ isOpen: false, message: '', type: 'success' });

  const validate = () => {
    const validation = {};

    if (!token) {
      validation.token = 'Reset token is missing.';
    }

    if (!form.newPassword) {
      validation.newPassword = 'New password is required.';
    } else if (form.newPassword.length < 8) {
      validation.newPassword = 'New password must be at least 8 characters.';
    }

    if (form.confirmPassword !== form.newPassword) {
      validation.confirmPassword = 'Confirm password must match the new password.';
    }

    setErrors(validation);
    return Object.keys(validation).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError('');

    if (!validate()) {
      return;
    }

    setLoading(true);

    try {
      await resetPassword(token, form.newPassword, form.confirmPassword);
      setNotification({
        isOpen: true,
        message: 'Password has been reset.',
        type: 'success',
      });
      setForm({
        newPassword: '',
        confirmPassword: '',
      });
    } catch (err) {
      setSubmitError(err.message || 'Unable to reset password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-layout">
        <aside className="auth-overview">
          <div>
            <h1 className="auth-main-title">IoT Compost Accelerator</h1>
            <h2>{authIntro.title}</h2>
            <p>{authIntro.description}</p>
          </div>
        </aside>

        <div className="auth-card">
          <h1>Create new password</h1>
          <p className="auth-subtitle">Choose a new password for your account.</p>

          {submitError && <p className="form-message error">{submitError}</p>}
          {errors.token && <p className="form-message error">{errors.token}</p>}

          <form onSubmit={handleSubmit} className="auth-form">
            <label>
              New password
              <PasswordInput
                value={form.newPassword}
                onPaste={(event) => event.preventDefault()}
                onDrop={(event) => event.preventDefault()}
                onChange={(event) =>
                  setForm({ ...form, newPassword: sanitizeInput(event.target.value) })
                }
              />
              {errors.newPassword && (
                <span className="auth-field-error">{errors.newPassword}</span>
              )}
            </label>

            <label>
              Confirm password
              <PasswordInput
                value={form.confirmPassword}
                onPaste={(event) => event.preventDefault()}
                onDrop={(event) => event.preventDefault()}
                onChange={(event) =>
                  setForm({ ...form, confirmPassword: sanitizeInput(event.target.value) })
                }
              />
              {errors.confirmPassword && (
                <span className="auth-field-error">{errors.confirmPassword}</span>
              )}
            </label>

            <button type="submit" className="primary-button" disabled={loading}>
              {loading ? 'Saving...' : 'Reset Password'}
            </button>
          </form>

          <div className="auth-footer auth-footer-right">
            <Link to="/">Back to login</Link>
          </div>
        </div>
      </div>

      <NotificationModal
        isOpen={notification.isOpen}
        title={notification.title}
        message={notification.message}
        type={notification.type}
        onClose={() => setNotification({ ...notification, isOpen: false })}
      />
    </div>
  );
}

export default ResetPassword;
