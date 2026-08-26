function SessionTimeoutModal({ open, onStayLoggedIn, onLogout }) {
  if (!open) {
    return null;
  }

  return (
    <div className="session-warning-overlay">
      <div className="session-warning-card">
        <h3>Session expiring soon due to inactivity.</h3>
        <p>Your session will expire in 1 minute unless you choose to stay signed in.</p>
        <div className="session-warning-actions">
          <button className="button button-secondary" onClick={onStayLoggedIn}>
            Stay Logged In
          </button>
          <button className="button button-danger" onClick={onLogout}>
            Logout
          </button>
        </div>
      </div>
    </div>
  );
}

export default SessionTimeoutModal;
