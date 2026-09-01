import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

function Layout({ user, title, subtitle, children, online, setOnline, hideSidebar = false }) {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const navItems = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/prediction', label: 'AI Prediction' },
    { path: '/logs', label: 'Logs / History' },
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
