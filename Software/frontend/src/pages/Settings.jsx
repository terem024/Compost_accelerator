import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../components/Layout.jsx';
import MeSection from '../components/settings/MeSection.jsx';
import ThresholdSection from '../components/settings/ThresholdSection.jsx';
import ActuatorSection from '../components/settings/ActuatorSection.jsx';
import useAppearance from '../hooks/useAppearance.js';

function Settings({ user, online, onLogout }) {
  const [appearance, updateAppearance] = useAppearance();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedSection = searchParams.get('section');
  const [activeSection, setActiveSection] = useState(
    ['me', 'controls', 'thresholds', 'appearance'].includes(requestedSection) ? requestedSection : 'me'
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
            <button className={activeSection === 'appearance' ? 'active' : ''}
              onClick={() => selectSection('appearance')}>Appearance</button>
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
            {activeSection === 'appearance' && (
              <section className="appearance-settings">
                <h2>Appearance</h2>
                <label htmlFor="font-size">Text size</label>
                <select id="font-size" value={appearance.fontSize}
                  onChange={(event) => updateAppearance({ fontSize: event.target.value })}>
                  <option value="standard">Standard</option>
                  <option value="large">Large</option>
                  <option value="extra-large">Extra large</option>
                </select>
                <p className="font-preview">Compost conditions and daily readings</p>
                <button type="button" className="secondary-button"
                  onClick={() => updateAppearance({ fontSize: 'standard' })}>Reset text size</button>
              </section>
            )}
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
