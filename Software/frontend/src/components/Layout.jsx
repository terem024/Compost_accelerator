import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, BrainCircuit, History, ClipboardList, Settings, Sprout } from 'lucide-react';
import ThemeToggle from './ThemeToggle.jsx';

function Layout({ user, title, subtitle, children, online, setOnline, hideSidebar = false }) {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const navItems = [
    { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { path: '/prediction', label: 'AI Prediction', icon: BrainCircuit },
    { path: '/logs', label: 'Logs / History', icon: History },
    { path: '/daily-reports', label: 'Daily Reports', icon: ClipboardList },
    { path: '/settings', label: 'Settings', icon: Settings }
  ];

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  return (
    <div className={`dashboard-shell ${hideSidebar ? 'full-screen' : ''}`}>
      {!hideSidebar && (
        <aside className="sidebar">
          <div className="brand"><Sprout size={28} aria-hidden="true" /><span>Compost Accelerator</span></div>
          <nav>
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={location.pathname === item.path ? 'active' : ''}
                aria-current={location.pathname === item.path ? 'page' : undefined}
              >
                <item.icon size={20} aria-hidden="true" />
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
            {location.pathname === '/dashboard' && <ThemeToggle />}
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
                aria-current={location.pathname === item.path ? 'page' : undefined}
              >
                <item.icon size={20} aria-hidden="true" />
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
