import { useEffect, useState } from 'react';
import Layout from '../components/Layout.jsx';
import { getActiveCompostBatch, getDailyReports } from '../services/api.js';

function formatNumber(value) {
  return value === null || value === undefined ? '--' : Number(value).toFixed(1);
}

function formatDate(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString() : '--';
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '--';
}

function ReportCard({ report }) {
  const metrics = [
    { label: 'Moisture', average: report.averageMoisture, minimum: report.minimumMoisture, maximum: report.maximumMoisture, unit: '%' },
    { label: 'Gas', average: report.averageGas, minimum: report.minimumGas, maximum: report.maximumGas, unit: '%' },
    { label: 'Temperature', average: report.averageTemperature, minimum: report.minimumTemperature, maximum: report.maximumTemperature, unit: '°C' },
    { label: 'Humidity', average: report.averageHumidity, minimum: report.minimumHumidity, maximum: report.maximumHumidity, unit: '%' },
  ];

  return (
    <div className="daily-report-card">
      <div className="daily-report-summary">
        <div>
          <span>Report date</span>
          <strong>{formatDate(report.reportDate)}</strong>
        </div>
        <div>
          <span>Readings received</span>
          <strong>{report.readingCount}</strong>
        </div>
        <div>
          <span>Generated</span>
          <strong>{formatDateTime(report.generatedAt)}</strong>
        </div>
      </div>

      <div className="daily-report-grid">
        {metrics.map((metric) => (
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
        <p>{report.sensorAvailabilityIssues || 'No sensor availability issues detected.'}</p>
      </div>
    </div>
  );
}

function batchLabel(report) {
  if (!report.batchId) return 'Unassigned legacy reports';
  const title = report.batchName || report.batchCode || `Batch ${report.batchId}`;
  const code = report.batchCode && report.batchCode !== title ? ` (${report.batchCode})` : '';
  return `${title}${code}`;
}

function DailyReports({ user, online }) {
  const [reports, setReports] = useState([]);
  const [activeBatch, setActiveBatch] = useState(null);
  const [selectedHistoryBatch, setSelectedHistoryBatch] = useState('');
  const [selectedHistoryDate, setSelectedHistoryDate] = useState('');
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
          setActiveBatch(batchResult.value || null);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const activeBatchReports = activeBatch
    ? reports.filter((report) => report.batchId === activeBatch.batchId)
    : [];
  const currentReport = activeBatchReports[0];

  const batchChoices = [];
  const batchChoiceIds = new Set();
  reports.forEach((report) => {
    const id = report.batchId ? String(report.batchId) : 'legacy';
    if (!batchChoiceIds.has(id)) {
      batchChoiceIds.add(id);
      batchChoices.push({ id, label: batchLabel(report) });
    }
  });

  const historyBatchId = selectedHistoryBatch || batchChoices[0]?.id || '';
  const historyReports = reports.filter(
    (report) => (report.batchId ? String(report.batchId) : 'legacy') === historyBatchId
  );
  const historyReport = historyReports.find(
    (report) => report.reportDate === selectedHistoryDate
  ) || historyReports[0];

  const handleBatchChange = (event) => {
    setSelectedHistoryBatch(event.target.value);
    setSelectedHistoryDate('');
  };

  return (
    <Layout
      user={user}
      title="Daily Reports"
      subtitle="Review daily sensor summaries by compost batch"
      online={online}
    >
      <section className="daily-report-section">
        <div className="daily-report-header">
          <div>
            <h3>Daily Sensor Report</h3>
            <p>Generated automatically every day at 6:00 AM for the previous day.</p>
            {activeBatch && <p>Current batch: {activeBatch.batchName || activeBatch.batchCode}</p>}
          </div>
        </div>

        {error && <p className="form-message error">{error}</p>}
        {loading ? (
          <div className="daily-report-empty">Loading daily reports...</div>
        ) : currentReport ? (
          <ReportCard report={currentReport} />
        ) : (
          <div className="daily-report-empty">
            {activeBatch
              ? 'No full-day report is available for the current batch yet.'
              : 'There is no active batch. Finished batch reports remain available in the history below.'}
          </div>
        )}
      </section>

      <section className="daily-report-section batch-report-history-section">
        <div className="daily-report-header">
          <div>
            <h3>Batch Report History</h3>
            <p>Saved summaries stay with their batch, including after the batch is finished.</p>
          </div>
        </div>

        {loading ? (
          <div className="daily-report-empty">Loading batch report history...</div>
        ) : batchChoices.length === 0 ? (
          <div className="daily-report-empty">No saved daily reports are available yet.</div>
        ) : (
          <>
            <div className="daily-report-history">
              <label htmlFor="historyBatch">Batch</label>
              <select id="historyBatch" value={historyBatchId} onChange={handleBatchChange}>
                {batchChoices.map((batch) => (
                  <option key={batch.id} value={batch.id}>{batch.label}</option>
                ))}
              </select>

              <label htmlFor="historyReportDate">Report date</label>
              <select
                id="historyReportDate"
                value={historyReport?.reportDate || ''}
                onChange={(event) => setSelectedHistoryDate(event.target.value)}
              >
                {historyReports.map((report) => (
                  <option key={report.reportId} value={report.reportDate}>
                    {formatDate(report.reportDate)}
                  </option>
                ))}
              </select>
              <span>{historyReports.length} saved report{historyReports.length === 1 ? '' : 's'}</span>
            </div>

            {historyReport && <ReportCard report={historyReport} />}
          </>
        )}
      </section>
    </Layout>
  );
}

export default DailyReports;
