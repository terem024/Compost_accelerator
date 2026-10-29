import { useRef, useState } from 'react';
import { X } from 'lucide-react';

export default function TermsNotice({ action = 'logging in' }) {
  const dialog = useRef(null);
  const [document, setDocument] = useState('terms');
  const openDocument = (value) => {
    setDocument(value);
    dialog.current?.showModal();
  };
  return (
    <>
      <p className="terms-login-notice">By {action}, you agree to our{' '}
        <button type="button" className="terms-link" onClick={() => openDocument('terms')}>Terms and Conditions</button>{' '}
        and acknowledge our{' '}
        <button type="button" className="terms-link" onClick={() => openDocument('privacy')}>Privacy Notice</button>.
      </p>
      <dialog ref={dialog} className="terms-dialog" aria-labelledby="terms-title">
        <header className="terms-dialog-header">
          <h2 id="terms-title">{document === 'privacy' ? 'Privacy Notice' : 'Terms and Conditions'}</h2>
          <button type="button" className="theme-toggle" aria-label="Close notice" title="Close notice"
            onClick={() => dialog.current.close()}><X size={20} /></button>
        </header>
        <div className="terms-dialog-body">
          <p>IoT-Based Compost Accelerator. Version: October 29, 2026.</p>
          {document === 'privacy' ? (
            <>
              <h3>Information processed</h3>
              <p>The system processes account details such as your name, email address, and password-verification data; login and session information; and compost batch records, readings, actuator logs, reports, and AI results.</p>
              <h3>How information is used</h3>
              <p>Information is used to manage accounts and sessions, operate compost monitoring and controls, generate reports and AI analysis, and investigate system problems. Do not add unnecessary personal or confidential details to batch names, descriptions, or notes.</p>
              <h3>Hosting and AI processing</h3>
              <p>The application and its records are hosted using Railway. Batch information and sensor and actuator summaries may be sent to the external AI provider, currently Google Gemini, to generate predictions. Avoid including sensitive information in those records.</p>
              <h3>Access and account safety</h3>
              <p>Project researchers and designated system administrators may access records for system operation and support. Keep your credentials private and sign out on shared devices.</p>
              <h3>Questions and requests</h3>
              <p>Contact the project researchers or designated barangay system administrator about access to your information, corrections, retention, removal requests, or privacy concerns. Ask them to explain the applicable record-handling arrangements before entering information you are unsure about.</p>
            </>
          ) : (
            <>
          <h3>Purpose and authorized use</h3>
          <p>This capstone system supports compost monitoring, batch records, automated spray and fan control, and AI-assisted analysis. Use it only with permission from the responsible project or barangay personnel.</p>
          <h3>Account responsibilities</h3>
          <p>Provide correct registration details, keep your password private, and sign out on shared devices. Do not access another person's account, alter records without permission, or interfere with system operation.</p>
          <h3>Sensor and equipment limitations</h3>
          <p>Readings can be affected by calibration, placement, connection faults, and sensor failure. A displayed actuator command is not proof that water was delivered or the fan physically operated. Verify unusual readings and equipment behavior before taking action.</p>
          <h3>AI limitations and human review</h3>
          <p>AI can make mistakes and produce inaccurate recommendations or readiness dates. Predictions are estimates, not a certification of compost maturity, safety, or suitability as fertilizer. Responsible personnel must physically inspect the compost before use or marking it ready.</p>
          <h3>Safe operation</h3>
          <p>Only trained personnel should inspect or change hardware wiring. Disconnect equipment power before wiring changes. Do not use the dashboard or AI output as a substitute for electrical safety procedures or direct equipment checks.</p>
          <h3>Data used by the system</h3>
          <p>The system processes account details, compost batch information, sensor readings, actuator logs, and reports. Batch information and monitoring records may be sent to an external AI provider to generate analysis. Do not enter passwords or unnecessary personal or confidential information in batch names, descriptions, or notes.</p>
          <h3>Questions and problems</h3>
          <p>Report account issues, incorrect records, unsafe behavior, or concerns about these terms to the project researchers or designated barangay system administrator before continuing the affected activity.</p>
            </>
          )}
        </div>
        <button type="button" className="primary-button" onClick={() => dialog.current.close()}>Close</button>
      </dialog>
    </>
  );
}
