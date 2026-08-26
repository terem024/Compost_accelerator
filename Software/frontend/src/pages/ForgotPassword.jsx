import { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../services/api.js';
import { sanitizeInput } from '../utils/sanitize.js';
import NotificationModal from '../components/NotificationModal.jsx';

const authIntro = {
  title: 'Build Your Skills With Compost Intelligence',
  description:
    'Monitor compost health and stay on top of environmental conditions with live sensor history, alerts, and actuator response simulation.',
};

function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notification, setNotification] = useState({ isOpen: false, message: '', type: 'success' });

  const validate = () => {
    const validation = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      validation.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      validation.email = 'Enter a valid email address.';
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
      await forgotPassword(email);
      setNotification({
        isOpen: true,
        message: 'Email has been sent.',
        type: 'success',
      });
      setEmail('');
    } catch (err) {
      setSubmitError(err.message || 'Unable to request password reset.');
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
          <h1>Reset your password</h1>
          <p className="auth-subtitle">Enter your registered email address.</p>

          {submitError && <p className="form-message error">{submitError}</p>}

          <form onSubmit={handleSubmit} className="auth-form">
            <label>
              Email
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(sanitizeInput(event.target.value))}
              />
              {errors.email && (
                <span className="auth-field-error">{errors.email}</span>
              )}
            </label>

            <button type="submit" className="primary-button" disabled={loading}>
              {loading ? 'Sending...' : 'Send Reset Link'}
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

export default ForgotPassword;
