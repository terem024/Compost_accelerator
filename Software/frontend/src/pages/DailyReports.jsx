import { useEffect, useState } from 'react';
import Layout from '../components/Layout.jsx';
import { getActiveCompostBatch, getDailyReports } from '../services/api.js';

function DailyReports({ user, online }) {
  const [reports, setReports] = useState([]);
  const [batchStartDate, setBatchStartDate] = useState('');
  const [selectedReportDate, setSelectedReportDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    Promise.allSettled([getDailyReports(), getActiveCompostBatch()])
      .then(([reportsResult, batchResult]) => {
        if (!active) return;

        if (reportsResult.status === 'fulfilled') {
          setReports(Array.isArray(reportsResult.value) ? reportsResult.value : []);
        } else {
          setError('Unable to load daily reports right now.');
        }

        if (batchResult.status === 'fulfilled') {
          setBatchStartDate(batchResult.value?.startDate || '');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  // The batch begins partway through its start date, so hide that partial day
  // and all report dates before it.
  const availableReports = batchStartDate
    ? reports.filter((report) => report.reportDate > batchStartDate)
    : reports;

  const selectedReport = availableReports.find(
    (report) => report.reportDate === selectedReportDate
  ) || availableReports[0];

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
          <div className="daily-report-empty">
            {batchStartDate
              ? 'No full-day reports are available since this batch started.'
              : 'No daily reports have been generated yet.'}
          </div>
        ) : (
          <>
            <div className="daily-report-history">
              <label htmlFor="dailyReportDate">Report date</label>
              <select
                id="dailyReportDate"
                value={selectedReport.reportDate}
                onChange={(event) => setSelectedReportDate(event.target.value)}
              >
                {availableReports.map((report) => (
                  <option key={report.reportId} value={report.reportDate}>
                    {formatDate(report.reportDate)}
                  </option>
                ))}
              </select>
              <span>{availableReports.length} saved report{availableReports.length === 1 ? '' : 's'}</span>
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
