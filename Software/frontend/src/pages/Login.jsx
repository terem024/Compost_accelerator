import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginUser } from '../services/api.js';
import { sanitizeInput } from '../utils/sanitize.js';
import NotificationModal from '../components/NotificationModal.jsx';
import PasswordInput from '../components/PasswordInput.jsx';

const authIntro = {
  title: 'Build Your Skills With Compost Intelligence',
  description:
    'Monitor compost health and stay on top of environmental conditions with live sensor history, alerts, and actuator response simulation.',
};

const preventPasswordPaste = (event) => {
  event.preventDefault();
};

function Login({ onLogin }) {
  const navigate = useNavigate();

  const [credentials, setCredentials] = useState({
    email: '',
    password: '',
  });

  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notification, setNotification] = useState({ isOpen: false, message: '', type: 'success' });

  const validate = () => {
    const validation = {};
    const email = credentials.email.trim();

    if (!email) {
      validation.email = 'Email or username is required.';
    }

    if (!credentials.password) {
      validation.password = 'Password is required.';
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
      const session = await loginUser({
        email: credentials.email.trim(),
        password: credentials.password,
      });
      onLogin(session);
      navigate('/dashboard');
    } catch (err) {
      setSubmitError(err.message || 'Login failed.');
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
          <h1>Welcome!</h1>
          <p className="auth-subtitle">Please login to your account.</p>

          {submitError && <p className="form-message error">{submitError}</p>}

          <form onSubmit={handleSubmit} className="auth-form">
            <label>
              Email or username
              <input
                type="text"
                value={credentials.email}
                onChange={(e) =>
                  setCredentials({ ...credentials, email: sanitizeInput(e.target.value) })
                }
              />
              {errors.email && (
                <span className="auth-field-error">{errors.email}</span>
              )}
            </label>

            <label>
              Password
              <PasswordInput
                value={credentials.password}
                onChange={(e) =>
                  setCredentials({ ...credentials, password: sanitizeInput(e.target.value) })
                }
                onPaste={preventPasswordPaste}
                onDrop={preventPasswordPaste}
              />
              {errors.password && (
                <span className="auth-field-error">{errors.password}</span>
              )}
            </label>

            <div className="auth-inline-link">
              <Link to="/forgot-password">Forgot Password?</Link>
            </div>

            <button type="submit" className="primary-button" disabled={loading}>
              {loading ? 'Logging in...' : 'Login'}
            </button>
          </form>

          <div className="auth-footer auth-footer-right">
            <span>Don&apos;t have an account?</span>
            <Link to="/register"> Sign up</Link>
          </div>
        </div>
      </div>

      <NotificationModal
        isOpen={notification.isOpen}
        message={notification.message}
        type={notification.type}
        onClose={() => setNotification({ ...notification, isOpen: false })}
      />
    </div>
  );
}

export default Login;
