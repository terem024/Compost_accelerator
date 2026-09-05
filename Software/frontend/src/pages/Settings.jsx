import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../components/Layout.jsx';
import MeSection from '../components/settings/MeSection.jsx';
import ThresholdSection from '../components/settings/ThresholdSection.jsx';
import ActuatorSection from '../components/settings/ActuatorSection.jsx';

function Settings({ user, online, onLogout }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedSection = searchParams.get('section');
  const [activeSection, setActiveSection] = useState(
    ['me', 'controls', 'thresholds'].includes(requestedSection) ? requestedSection : 'me'
  );

  const selectSection = (section) => {
    setActiveSection(section);
    setSearchParams(section === 'me' ? {} : { section }, { replace: true });
  };

  return (
    <Layout
      user={user}
      title="Settings"
      subtitle=""
      online={online}
      hideSidebar={true}
    >
      <div className="settings-page-wrapper">
        <div className="settings-header-row">
          <div className="settings-header-left">
            <button className="back-button" onClick={() => navigate('/dashboard')}>
              ← Back
            </button>
            <h1 className="settings-main-title">Settings</h1>
          </div>
        </div>

        <div className="settings-screen">
          <aside className="settings-inner-sidebar">
            <button
              className={activeSection === 'me' ? 'active' : ''}
              onClick={() => selectSection('me')}
            >
              Me
            </button>
            <button
              className={activeSection === 'controls' ? 'active' : ''}
              onClick={() => selectSection('controls')}
            >
              Batch & Actuator Controls
            </button>
            <button
              className={activeSection === 'thresholds' ? 'active' : ''}
              onClick={() => selectSection('thresholds')}
            >
              Threshold Settings
            </button>
          </aside>

          <div className="settings-panel-content">
            {activeSection === 'me' && (
              <MeSection user={user} onLogout={onLogout} />
            )}

            {activeSection === 'controls' && (
              <ActuatorSection />
            )}

            {activeSection === 'thresholds' && (
              <ThresholdSection />
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default Settings;
