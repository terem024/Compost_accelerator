import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerUser, sendOtpEmail, verifyOtp } from '../services/api.js';
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

function Register({ onRegister }) {
  const navigate = useNavigate();

  const [profile, setProfile] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [loading, setLoading] = useState(false);

  // OTP step
  const [otpSent, setOtpSent] = useState(false);
  const [otpValue, setOtpValue] = useState('');
  const [notification, setNotification] = useState({
    isOpen: false,
    message: '',
    title: 'Notice',
    type: 'info',
  });

  const validate = () => {
    const validation = {};
    const name = (profile.name || '').trim();
    const email = (profile.email || '').trim();

    if (!name) {
      validation.name = 'Full name is required.';
    }

    if (!email) {
      validation.email = 'Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      validation.email = 'Enter a valid email address.';
    }

    if (!profile.password) {
      validation.password = 'Password field cannot be empty.';
    } else if (profile.password.length < 8) {
      validation.password = 'Password must be at least 8 characters.';
    }

    if (!profile.confirmPassword) {
      validation.confirmPassword = 'Confirm password field cannot be empty.';
    } else if (profile.password && profile.confirmPassword !== profile.password) {
      validation.confirmPassword = 'Password dont match.';
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
      const session = await registerUser({
        ...profile,
        name: profile.name.trim(),
        email: profile.email.trim(),
      });
      onRegister(session);
      navigate('/dashboard');
    } catch (err) {
      setSubmitError(err.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  // Replace registration submission with an OTP verification step
  const handleSubmitOtpStep = async (event) => {
    event.preventDefault();
    setSubmitError('');

    if (!validate()) {
      return;
    }

    setLoading(true);

    try {
      await sendOtpEmail(profile.email);
      setOtpSent(true);
      setNotification({
        isOpen: true,
        title: 'Account Verification',
        message: 'OTP sent to your email. Please verify your email address.',
        type: 'info',
      });
    } catch (err) {
      setSubmitError(err.message || 'Unable to send OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    setSubmitError('');

    if (!otpValue) {
      setSubmitError('Please enter the OTP.');
      return;
    }

    setLoading(true);

    try {
      await verifyOtp(profile.email, otpValue);

      const session = await registerUser({
        ...profile,
        name: profile.name.trim(),
        email: profile.email.trim(),
      });
      setOtpSent(false);
      onRegister(session);
      navigate('/dashboard');
    } catch (err) {
      setSubmitError(err.message || 'Invalid OTP. Please try again.');
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
          <h1>Create your account</h1>
          <p className="auth-subtitle">Join your compost accelerator management system.</p>

          {submitError && <p className="form-message error">{submitError}</p>}

          <form onSubmit={handleSubmitOtpStep} className="auth-form">
          <label>
            Full name
            <input
              type="text"
              value={profile.name}
              onChange={(e) =>
                setProfile({ ...profile, name: sanitizeInput(e.target.value) })
              }
            />
            {errors.name && (
              <span className="auth-field-error">{errors.name}</span>
            )}
          </label>

          <label>
            Email
            <input
              type="email"
              value={profile.email}
              onChange={(e) =>
                setProfile({ ...profile, email: sanitizeInput(e.target.value) })
              }
            />
            {errors.email && (
              <span className="auth-field-error">{errors.email}</span>
            )}
          </label>

          <label>
            Password
            <PasswordInput
              value={profile.password}
              onChange={(e) =>
                setProfile({ ...profile, password: sanitizeInput(e.target.value) })
              }
              onPaste={preventPasswordPaste}
              onDrop={preventPasswordPaste}
            />
            {errors.password && (
              <span className="auth-field-error">{errors.password}</span>
            )}
          </label>

          <label>
            Confirm password
            <PasswordInput
              value={profile.confirmPassword}
              onChange={(e) =>
                setProfile({ ...profile, confirmPassword: sanitizeInput(e.target.value) })
              }
              onPaste={preventPasswordPaste}
              onDrop={preventPasswordPaste}
            />
            {errors.confirmPassword && (
              <span className="auth-field-error">{errors.confirmPassword}</span>
            )}
          </label>

          <button type="submit" className="primary-button" disabled={loading}>
            {loading ? 'Creating account...' : 'Register'}
          </button>

          {otpSent && (
            <div style={{ marginTop: 12 }}>
              <label>
                Enter OTP (sent to your email)
                <input
                  type="text"
                  value={otpValue}
                  onChange={(e) => setOtpValue(sanitizeInput(e.target.value))}
                />
              </label>
              <div style={{ marginTop: 8 }}>
                <button type="button" className="primary-button" onClick={handleVerifyOtp} disabled={loading}>
                  {loading ? 'Verifying...' : 'Verify OTP'}
                </button>
              </div>
            </div>
          )}
        </form>

        <div className="auth-footer auth-footer-right">
          <span>Already have an account?</span>
          <Link to="/"> Login</Link>
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

export default Register;
