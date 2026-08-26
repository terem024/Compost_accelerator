import { Link, useLocation } from 'react-router-dom';

function Layout({ user, title, subtitle, children, online, setOnline, hideSidebar = false }) {
  const location = useLocation();
  const navItems = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/prediction', label: 'AI Prediction' },
    { path: '/logs', label: 'Logs / History' },
    { path: '/settings', label: 'Settings' }
  ];

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
          <div>
            <div className="topbar-label">{title}</div>
            <div className="topbar-subtext">{subtitle}</div>
          </div>
          <div className="topbar-right">
            <div className={`status-chip ${online ? 'online' : 'offline'}`}>
              {online ? '🟢 Online' : '🔴 Offline'}
            </div>
            <div className="profile-chip">{user?.name || 'User'}</div>
          </div>
        </header>

        <section className="dashboard-content">{children}</section>
      </main>
    </div>
  );
}

export default Layout;
