import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Prediction from './pages/Prediction.jsx';
import Logs from './pages/Logs.jsx';
import Settings from './pages/Settings.jsx';
import ErrorPage from './pages/ErrorPage.jsx';
import useInactivityTimeout from './hooks/useInactivityTimeout.jsx';
import AppErrorBoundary from './components/AppErrorBoundary.jsx';
import SessionTimeoutModal from './components/SessionTimeoutModal.jsx';
import ToastContainer from './components/ToastContainer.jsx';
import {
  getStoredAuthSession,
  logoutUser,
  validateSession,
} from './services/api.js';

function App() {
  const location = useLocation();
  const [authSession, setAuthSession] = useState(() => getStoredAuthSession());
  const [checkingSession, setCheckingSession] = useState(true);
  const [sessionError, setSessionError] = useState(null);
  const [sessionAttempt, setSessionAttempt] = useState(0);
  const [online] = useState(true);
  const user = authSession?.user || null;

  useEffect(() => {
    let revealTimer;

    const revealFocusedAuthField = () => {
      window.clearTimeout(revealTimer);
      revealTimer = window.setTimeout(() => {
        const field = document.activeElement;
        if (field instanceof HTMLElement && field.matches('.auth-page input')) {
          field.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
        }
      }, 180);
    };

    document.addEventListener('focusin', revealFocusedAuthField);
    window.visualViewport?.addEventListener('resize', revealFocusedAuthField);

    return () => {
      window.clearTimeout(revealTimer);
      document.removeEventListener('focusin', revealFocusedAuthField);
      window.visualViewport?.removeEventListener('resize', revealFocusedAuthField);
    };
  }, []);

  useEffect(() => {
    let active = true;
    const storedSession = getStoredAuthSession();
    setSessionError(null);

    if (!storedSession) {
      setAuthSession(null);
      setCheckingSession(false);
      return undefined;
    }

    setCheckingSession(true);
    validateSession()
      .then((session) => {
        if (active) {
          setAuthSession(session);
        }
      })
      .catch((error) => {
        if (active) {
          if (error.status === 401 || !getStoredAuthSession()) {
            setAuthSession(null);
          } else {
            setSessionError("We couldn't reconnect to your account. Please try again shortly.");
          }
        }
      })
      .finally(() => {
        if (active) {
          setCheckingSession(false);
        }
      });

    return () => {
      active = false;
    };
  }, [sessionAttempt]);

  useEffect(() => {
    if (!sessionError) return undefined;
    const retry = () => setSessionAttempt((attempt) => attempt + 1);
    const timer = window.setTimeout(retry, 10000);
    window.addEventListener('online', retry);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('online', retry);
    };
  }, [sessionError]);

  const handleLogin = useCallback((session) => {
    setAuthSession(session);
  }, []);

  const handleLogout = useCallback(async () => {
    try {
      await logoutUser();
    } catch {
      // Local logout must still complete when the server is unavailable.
    } finally {
      setAuthSession(null);
      setSessionError(null);
    }
  }, []);

  const { showWarning, resetTimer } = useInactivityTimeout(
    handleLogout, !!user && !checkingSession && !sessionError,
  );

  const handleStayLoggedIn = async () => {
    try {
      const session = await validateSession();
      setAuthSession(session);
      resetTimer();
    } catch (error) {
      if (error.status === 401 || !getStoredAuthSession()) {
        setAuthSession(null);
      } else {
        setSessionError("We couldn't reconnect to your account. Please try again shortly.");
      }
    }
  };

  if (checkingSession) {
    return <div className="app-loading" role="status">Checking session...</div>;
  }

  if (sessionError) {
    return (
      <main className="app-loading">
        <section className="session-recovery" role="alert">
          <h1>Connection interrupted</h1>
          <p>{sessionError}</p>
          <button className="button button-secondary" onClick={() => setSessionAttempt((attempt) => attempt + 1)}>
            Try again
          </button>
        </section>
      </main>
    );
  }

  return (
    <>
      <SessionTimeoutModal
        open={showWarning}
        onStayLoggedIn={handleStayLoggedIn}
        onLogout={handleLogout}
      />
      <ToastContainer />

      <AppErrorBoundary resetKey={location.pathname} homePath={user ? '/dashboard' : '/'}>
        <Routes>
          <Route
            path="/"
            element={
              user ? (
                <Navigate to="/dashboard" replace />
              ) : (
                <Login onLogin={handleLogin} />
              )
            }
          />

        <Route
          path="/register"
          element={
            user ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <Register onRegister={handleLogin} />
            )
          }
        />

        <Route
          path="/forgot-password"
          element={<ForgotPassword />}
        />

        <Route
          path="/reset-password"
          element={<ResetPassword />}
        />

        <Route
          path="/dashboard"
          element={
            user ? (
              <Dashboard user={user} online={online} />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />

        <Route
          path="/prediction"
          element={
            user ? (
              <Prediction user={user} online={online} />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />

        <Route
          path="/logs"
          element={
            user ? (
              <Logs user={user} online={online} />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />

        <Route
          path="/settings"
          element={
            user ? (
              <Settings user={user} online={online} onLogout={handleLogout} />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />

          <Route
            path="*"
            element={
              <ErrorPage
                type="notFound"
                homePath={user ? '/dashboard' : '/'}
              />
            }
          />
        </Routes>
      </AppErrorBoundary>
    </>);
}

export default App;
