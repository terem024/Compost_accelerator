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
  clearStoredAuthSession,
  getStoredAuthSession,
  logoutUser,
  validateSession,
} from './services/api.js';

function App() {
  const location = useLocation();
  const [authSession, setAuthSession] = useState(() => getStoredAuthSession());
  const [checkingSession, setCheckingSession] = useState(true);
  const [online] = useState(true);
  const user = authSession?.user || null;

  useEffect(() => {
    let active = true;
    const storedSession = getStoredAuthSession();

    if (!storedSession) {
      setCheckingSession(false);
      return undefined;
    }

    validateSession()
      .then((session) => {
        if (active) {
          setAuthSession(session);
        }
      })
      .catch(() => {
        clearStoredAuthSession();
        if (active) {
          setAuthSession(null);
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
  }, []);

  const handleLogin = useCallback((session) => {
    setAuthSession(session);
  }, []);

  const handleLogout = useCallback(async () => {
    await logoutUser();
    setAuthSession(null);
  }, []);

  const { showWarning, resetTimer } = useInactivityTimeout(handleLogout, !!user);

  const handleStayLoggedIn = async () => {
    try {
      const session = await validateSession();
      setAuthSession(session);
      resetTimer();
    } catch {
      await handleLogout();
    }
  };

  if (checkingSession) {
    return <div className="app-loading">Checking session...</div>;
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
