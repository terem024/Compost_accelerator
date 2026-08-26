import { useEffect, useState, useRef, useCallback } from 'react';
import { useLocation } from 'react-router-dom';

const WARNING_DELAY_MS = 14 * 60 * 1000;
const LOGOUT_DELAY_MS = 15 * 60 * 1000;

export default function useInactivityTimeout(onLogout, enabled = false) {
  const [showWarning, setShowWarning] = useState(false);
  const warningTimer = useRef(null);
  const logoutTimer = useRef(null);
  const location = useLocation();

  const clearTimers = useCallback(() => {
    if (warningTimer.current) {
      clearTimeout(warningTimer.current);
      warningTimer.current = null;
    }
    if (logoutTimer.current) {
      clearTimeout(logoutTimer.current);
      logoutTimer.current = null;
    }
  }, []);

  const scheduleTimers = useCallback(() => {
    if (!enabled) {
      return;
    }

    clearTimers();
    setShowWarning(false);

    warningTimer.current = window.setTimeout(() => {
      setShowWarning(true);
    }, WARNING_DELAY_MS);

    logoutTimer.current = window.setTimeout(() => {
      setShowWarning(false);
      onLogout();
    }, LOGOUT_DELAY_MS);
  }, [clearTimers, enabled, onLogout]);

  useEffect(() => {
    if (!enabled) {
      clearTimers();
      return;
    }

    scheduleTimers();
    return clearTimers;
  }, [enabled, location.pathname, scheduleTimers, clearTimers]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const events = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];
    const handleActivity = () => {
      scheduleTimers();
    };

    events.forEach((event) => window.addEventListener(event, handleActivity));

    return () => {
      events.forEach((event) => window.removeEventListener(event, handleActivity));
    };
  }, [enabled, scheduleTimers]);

  useEffect(() => {
    return clearTimers;
  }, [clearTimers]);

  return {
    showWarning,
    resetTimer: scheduleTimers,
  };
}
