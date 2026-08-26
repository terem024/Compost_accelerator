import { useState } from 'react';

function EyeIcon() {
  return (
    <svg
      aria-hidden="true"
      className="password-input-icon"
      fill="none"
      focusable="false"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
    >
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg
      aria-hidden="true"
      className="password-input-icon"
      fill="none"
      focusable="false"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
    >
      <path d="m3 3 18 18" />
      <path d="M10.7 5.2A10.7 10.7 0 0 1 12 5c6.5 0 10 7 10 7a17.4 17.4 0 0 1-2.4 3.5" />
      <path d="M6.3 6.3C3.6 8.1 2 12 2 12s3.5 7 10 7c1.9 0 3.5-.6 4.9-1.4" />
      <path d="M9.9 9.9A3 3 0 0 0 14.1 14.1" />
    </svg>
  );
}

function PasswordInput({ className = '', containerClassName = '', disabled, ...inputProps }) {
  const [isVisible, setIsVisible] = useState(false);
  const label = isVisible ? 'Hide password' : 'Show password';

  return (
    <div className={`password-input ${containerClassName}`.trim()}>
      <input
        {...inputProps}
        className={className}
        disabled={disabled}
        type={isVisible ? 'text' : 'password'}
      />
      <button
        type="button"
        className="password-input-toggle"
        aria-label={label}
        aria-pressed={isVisible}
        disabled={disabled}
        onClick={() => setIsVisible((visible) => !visible)}
      >
        {isVisible ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  );
}

export default PasswordInput;
