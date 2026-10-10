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
  const title = report.batchName || report.batchCode || `Batch ${report.batchId}`;
  const code = report.batchCode && report.batchCode !== title ? ` (${report.batchCode})` : '';
  return `${title}${code}`;
}

function DailyReports({ user, online }) {
  const [reports, setReports] = useState([]);
  const [activeBatch, setActiveBatch] = useState(null);
  const [selectedActiveDate, setSelectedActiveDate] = useState('');
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
  const currentReport = activeBatchReports.find(
    (report) => report.reportDate === selectedActiveDate
  ) || activeBatchReports[0];

  const completedReportsByBatch = new Map();
  reports
    .filter((report) => report.batchId && report.batchStatus === 'COMPLETED')
    .forEach((report) => {
      if (!completedReportsByBatch.has(report.batchId)) {
        completedReportsByBatch.set(report.batchId, {
          label: batchLabel(report),
          reports: [],
        });
      }
      completedReportsByBatch.get(report.batchId).reports.push(report);
    });

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
          <>
            <div className="daily-report-history">
              <label htmlFor="activeReportDate">Report date</label>
              <select
                id="activeReportDate"
                value={currentReport.reportDate}
                onChange={(event) => setSelectedActiveDate(event.target.value)}
              >
                {activeBatchReports.map((report) => (
                  <option key={report.reportId} value={report.reportDate}>
                    {formatDate(report.reportDate)}
                  </option>
                ))}
              </select>
              <span>{activeBatchReports.length} saved report{activeBatchReports.length === 1 ? '' : 's'} for this batch</span>
            </div>
            <ReportCard report={currentReport} />
          </>
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
          </div>
        </div>

        {loading ? (
          <div className="daily-report-empty">Loading batch report history...</div>
        ) : completedReportsByBatch.size === 0 ? (
          <div className="daily-report-empty">
            No completed batch reports yet. Daily summaries will appear here after a batch is finished.
          </div>
        ) : (
          <div className="batch-history-groups">
            {[...completedReportsByBatch.entries()].map(([batchId, batch]) => (
              <section className="batch-history-group" key={batchId}>
                <div className="batch-history-heading">
                  <h4>{batch.label}</h4>
                  <span>Completed · {batch.reports.length} daily report{batch.reports.length === 1 ? '' : 's'}</span>
                </div>
                <div className="batch-history-entries">
                  {batch.reports.map((report) => {
                    const metrics = [
                      { label: 'Moisture', average: report.averageMoisture, minimum: report.minimumMoisture, maximum: report.maximumMoisture, unit: '%' },
                      { label: 'Gas', average: report.averageGas, minimum: report.minimumGas, maximum: report.maximumGas, unit: '%' },
                      { label: 'Temperature', average: report.averageTemperature, minimum: report.minimumTemperature, maximum: report.maximumTemperature, unit: '°C' },
                      { label: 'Humidity', average: report.averageHumidity, minimum: report.minimumHumidity, maximum: report.maximumHumidity, unit: '%' },
                    ];
                    return (
                      <details className="batch-history-entry" key={report.reportId}>
                        <summary>
                          <span>{formatDate(report.reportDate)}</span>
                          <span>{report.readingCount} readings</span>
                          <span>Generated {formatDateTime(report.generatedAt)}</span>
                        </summary>
                        <dl className="batch-history-metrics">
                          {metrics.map((metric) => (
                            <div key={metric.label}>
                              <dt>{metric.label}</dt>
                              <dd>
                                {formatNumber(metric.average)} {metric.unit}
                                <small>Min {formatNumber(metric.minimum)} · Max {formatNumber(metric.maximum)} {metric.unit}</small>
                              </dd>
                            </div>
                          ))}
                        </dl>
                        <p className="batch-history-issues">
                          <strong>Sensor availability:</strong> {report.sensorAvailabilityIssues || 'No issues detected.'}
                        </p>
                      </details>
                    );
                  })}
                </div>
              </section>
            ))}
          </div>
        )}
      </section>
    </Layout>
  );
}

export default DailyReports;
