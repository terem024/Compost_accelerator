import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Sun, Moon } from 'lucide-react';
import useAppearance from '../hooks/useAppearance.js';

function Layout({ user, title, subtitle, children, online, setOnline, hideSidebar = false }) {
  const location = useLocation();
  const [appearance, updateAppearance] = useAppearance();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const navItems = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/prediction', label: 'AI Prediction' },
    { path: '/logs', label: 'Logs / History' },
    { path: '/daily-reports', label: 'Daily Reports' },
    { path: '/settings', label: 'Settings' }
  ];

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  return (
    <div className={`dashboard-shell ${hideSidebar ? 'full-screen' : ''}`}>
      {!hideSidebar && (
        <aside className="sidebar">
          <div className="brand">Compost Accelerator</div>
          <nav>
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={location.pathname === item.path ? 'active' : ''}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </aside>
      )}

      <main className="dashboard-main">
        <header className="topbar">
          <div className="topbar-heading">
            {!hideSidebar && (
              <button
                type="button"
                className="mobile-nav-toggle"
                aria-label={mobileNavOpen ? 'Close navigation' : 'Open navigation'}
                aria-expanded={mobileNavOpen}
                aria-controls="mobile-navigation"
                title={mobileNavOpen ? 'Close navigation' : 'Open navigation'}
                onClick={() => setMobileNavOpen((open) => !open)}
              >
                <span aria-hidden="true" />
                <span aria-hidden="true" />
                <span aria-hidden="true" />
              </button>
            )}
            <div className="topbar-copy">
              <div className="topbar-label">{title}</div>
              <div className="topbar-subtext">{subtitle}</div>
            </div>
          </div>
          <div className="topbar-right">
            {location.pathname === '/dashboard' && (
              <button type="button" className="theme-toggle" role="switch"
                aria-checked={appearance.theme === 'light'} aria-label="Light mode"
                title={appearance.theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
                onClick={() => updateAppearance({ theme: appearance.theme === 'light' ? 'dark' : 'light' })}>
                {appearance.theme === 'light' ? <Sun size={20} /> : <Moon size={20} />}
              </button>
            )}
            <div className={`status-chip ${online ? 'online' : 'offline'}`}>
              <span className="status-dot" aria-hidden="true" />
              {online ? 'Online' : 'Offline'}
            </div>
            <div className="profile-chip">{user?.name || 'User'}</div>
          </div>
        </header>

        {!hideSidebar && (
          <nav
            id="mobile-navigation"
            className={`mobile-nav ${mobileNavOpen ? 'open' : ''}`}
            aria-label="Main navigation"
          >
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={location.pathname === item.path ? 'active' : ''}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        )}

        <section className="dashboard-content">{children}</section>
      </main>
    </div>
  );
}

export default Layout;
