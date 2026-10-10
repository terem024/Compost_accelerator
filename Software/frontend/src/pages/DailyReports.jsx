import { useEffect, useState } from 'react';
import Layout from '../components/Layout.jsx';
import { getDailyReports } from '../services/api.js';

function DailyReports({ user, online }) {
  const [reports, setReports] = useState([]);
  const [selectedReportDate, setSelectedReportDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    getDailyReports()
      .then((data) => {
        if (active) setReports(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (active) setError('Unable to load daily reports right now.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const selectedReport = reports.find(
    (report) => report.reportDate === selectedReportDate
  ) || reports[0];

  const formatNumber = (value) => (
    value === null || value === undefined ? '--' : Number(value).toFixed(1)
  );

  const formatDate = (value) => (
    value ? new Date(`${value}T00:00:00`).toLocaleDateString() : '--'
  );

  const formatDateTime = (value) => (
    value ? new Date(value).toLocaleString() : '--'
  );

  const reportMetrics = selectedReport ? [
    { label: 'Moisture', average: selectedReport.averageMoisture, minimum: selectedReport.minimumMoisture, maximum: selectedReport.maximumMoisture, unit: '%' },
    { label: 'Gas', average: selectedReport.averageGas, minimum: selectedReport.minimumGas, maximum: selectedReport.maximumGas, unit: '%' },
    { label: 'Temperature', average: selectedReport.averageTemperature, minimum: selectedReport.minimumTemperature, maximum: selectedReport.maximumTemperature, unit: '°C' },
    { label: 'Humidity', average: selectedReport.averageHumidity, minimum: selectedReport.minimumHumidity, maximum: selectedReport.maximumHumidity, unit: '%' },
  ] : [];

  return (
    <Layout
      user={user}
      title="Daily Reports"
      subtitle="Review saved daily sensor summaries"
      online={online}
    >
      <section className="daily-report-section">
        <div className="daily-report-header">
          <div>
            <h3>Daily Sensor Report</h3>
            <p>Generated automatically every day at 6:00 AM for the previous day.</p>
          </div>
        </div>

        {error && <p className="form-message error">{error}</p>}

        {loading ? (
          <div className="daily-report-empty">Loading daily reports...</div>
        ) : !selectedReport ? (
          <div className="daily-report-empty">No daily reports have been generated yet.</div>
        ) : (
          <>
            <div className="daily-report-history">
              <label htmlFor="dailyReportDate">Report date</label>
              <select
                id="dailyReportDate"
                value={selectedReport.reportDate}
                onChange={(event) => setSelectedReportDate(event.target.value)}
              >
                {reports.map((report) => (
                  <option key={report.reportId} value={report.reportDate}>
                    {formatDate(report.reportDate)}
                  </option>
                ))}
              </select>
              <span>{reports.length} saved report{reports.length === 1 ? '' : 's'}</span>
            </div>

            <div className="daily-report-card">
              <div className="daily-report-summary">
                <div>
                  <span>Report date</span>
                  <strong>{formatDate(selectedReport.reportDate)}</strong>
                </div>
                <div>
                  <span>Readings received</span>
                  <strong>{selectedReport.readingCount}</strong>
                </div>
                <div>
                  <span>Generated</span>
                  <strong>{formatDateTime(selectedReport.generatedAt)}</strong>
                </div>
              </div>

              <div className="daily-report-grid">
                {reportMetrics.map((metric) => (
                  <div className="daily-report-metric" key={metric.label}>
                    <span>{metric.label}</span>
                    <strong>{formatNumber(metric.average)} {metric.unit}</strong>
                    <small>
                      Min {formatNumber(metric.minimum)} {metric.unit} · Max {formatNumber(metric.maximum)} {metric.unit}
                    </small>
                  </div>
                ))}
              </div>

              <div className="daily-report-issues">
                <span>Sensor availability</span>
                <p>{selectedReport.sensorAvailabilityIssues || 'No sensor availability issues detected.'}</p>
              </div>
            </div>
          </>
        )}
      </section>
    </Layout>
  );
}

export default DailyReports;
