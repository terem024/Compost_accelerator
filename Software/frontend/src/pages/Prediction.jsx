import { useEffect, useMemo, useState } from 'react';
import Layout from '../components/Layout.jsx';
import {
  generateAIPrediction,
  getActiveCompostBatch,
  getAIPredictionAvailability,
  getCompostBatches,
  getSensorReadings,
} from '../services/api.js';

const SENSOR_SERIES = [
  { id: 'moisture', label: 'Moisture', field: 'moistureLevel', color: '#60A5FA', unit: '%' },
  { id: 'gas', label: 'Gas', field: 'gasLevel', color: '#f97316', unit: '%' },
  { id: 'temperature', label: 'Temperature', field: 'temperatureC', color: '#fb7185', unit: '\u00B0C' },
  { id: 'humidity', label: 'Humidity', field: 'humidityLevel', color: '#34d399', unit: '%' },
];

function formatStatus(value) {
  if (!value) return 'Unknown';
  if (value === 'NORMAL') return 'Optimal';
  return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
}

function formatValue(value) {
  return value === null || value === undefined ? '--' : value.toFixed(1);
}

function formatAxisValue(value) {
  if (!Number.isFinite(value)) return '--';
  return Math.abs(value) >= 100 ? Math.round(value).toString() : value.toFixed(1);
}

function formatAxisTime(value) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return { time: '--', date: '' };
  }

  return {
    time: date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    date: date.toLocaleDateString([], { month: 'short', day: 'numeric' }),
  };
}

function formatCountdown(totalSeconds) {
  const seconds = Math.max(0, totalSeconds);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;

  return [hours, minutes, remainingSeconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
}

function Prediction({ user, online }) {
  const [batchId, setBatchId] = useState('');
  const [activeBatch, setActiveBatch] = useState(null);
  const [batches, setBatches] = useState([]);
  const [prediction, setPrediction] = useState(null);
  const [loading, setLoading] = useState(false);
  const [predictionError, setPredictionError] = useState(null);
  const [predictionModalOpen, setPredictionModalOpen] = useState(false);
  const [predictionAvailability, setPredictionAvailability] = useState(null);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [countdownSeconds, setCountdownSeconds] = useState(0);
  const [sensorHistory, setSensorHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [activeSeries, setActiveSeries] = useState({
    moisture: true,
    gas: true,
    temperature: true,
    humidity: true,
  });
  const [hoverInfo, setHoverInfo] = useState(null);

  useEffect(() => {
    async function loadSensorHistory() {
      try {
        const [active, batchList, readings] = await Promise.all([
          getActiveCompostBatch().catch(() => null),
          getCompostBatches(),
          getSensorReadings(),
        ]);

        const availableBatches = Array.isArray(batchList) ? batchList : [];
        setActiveBatch(active);
        setBatches(availableBatches);
        setBatchId(active?.batchId || availableBatches[0]?.batchId || '');

        const history = Array.isArray(readings) ? readings : [];
        setSensorHistory(history);
      } catch {
        setSensorHistory([]);
      } finally {
        setHistoryLoading(false);
      }
    }

    loadSensorHistory();
  }, []);

  useEffect(() => {
    if (!batchId) {
      setPredictionAvailability(null);
      return undefined;
    }

    let cancelled = false;
    setAvailabilityLoading(true);
    setPrediction(null);
    setPredictionError(null);

    getAIPredictionAvailability(batchId)
      .then((availability) => {
        if (cancelled) return;
        setPredictionAvailability(availability);
        setPrediction(availability.prediction || null);
      })
      .catch(() => {
        if (!cancelled) setPredictionAvailability(null);
      })
      .finally(() => {
        if (!cancelled) setAvailabilityLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [batchId]);

  useEffect(() => {
    const nextPredictionAt = predictionAvailability?.nextPredictionAt;
    if (!nextPredictionAt) {
      setCountdownSeconds(0);
      return undefined;
    }

    const updateCountdown = () => {
      const remaining = Math.max(
        0,
        Math.ceil((new Date(nextPredictionAt).getTime() - Date.now()) / 1000)
      );
      setCountdownSeconds(remaining);
    };

    updateCountdown();
    const intervalId = window.setInterval(updateCountdown, 1000);
    return () => window.clearInterval(intervalId);
  }, [predictionAvailability?.nextPredictionAt]);

  const visibleSensors = SENSOR_SERIES.filter((series) => activeSeries[series.id]);
  const selectedBatch = useMemo(
    () => batches.find((batch) => String(batch.batchId) === String(batchId)) || null,
    [batchId, batches]
  );
  const selectedBatchHistory = useMemo(
    () => sensorHistory
      .filter((reading) => String(reading.batchId) === String(batchId))
      .slice(0, 60)
      .reverse(),
    [batchId, sensorHistory]
  );
  const dailyLimitActive = Boolean(
    predictionAvailability
      && !predictionAvailability.canGenerate
      && countdownSeconds > 0
  );

  const chartData = useMemo(() => {
    const history = selectedBatchHistory.slice(-40);
    const dataValues = [];

    history.forEach((reading) => {
      visibleSensors.forEach((series) => {
        const value = reading[series.field];
        if (value !== null && value !== undefined) {
          dataValues.push(value);
        }
      });
    });

    const minValue = dataValues.length ? Math.min(...dataValues) : 0;
    const maxValue = dataValues.length ? Math.max(...dataValues) : 100;
    const range = Math.max(maxValue - minValue, 10);
    const chartMin = Math.max(0, minValue - range * 0.1);
    const chartMax = maxValue + range * 0.1;

    const width = 1120;
    const height = 420;
    const padding = 76;
    const plotWidth = width - padding * 2;
    const plotHeight = height - padding * 2;
    const xStep = history.length > 1 ? plotWidth / (history.length - 1) : plotWidth;
    const yTicks = Array.from({ length: 5 }).map((_, index) => {
      const ratio = index / 4;
      const value = chartMax - (chartMax - chartMin) * ratio;
      const y = padding + plotHeight * ratio;
      return { value, y };
    });
    const xTickCount = Math.min(5, history.length);
    const xTicks = Array.from({ length: xTickCount }).map((_, index) => {
      const readingIndex = xTickCount === 1
        ? 0
        : Math.round((index * (history.length - 1)) / (xTickCount - 1));
      const reading = history[readingIndex];
      const x = padding + readingIndex * xStep;
      return { reading, x };
    });

    const seriesLines = visibleSensors.map((series) => {
      const points = history
        .map((reading, index) => {
          const value = reading[series.field];
          if (value === null || value === undefined) return null;

          const x = padding + index * xStep;
          const y = padding + plotHeight * (1 - (value - chartMin) / (chartMax - chartMin));
          return { x, y, value, reading, series };
        })
        .filter(Boolean);

      return {
        series,
        points,
        path: points.map((point) => `${point.x},${point.y}`).join(' '),
      };
    });

    return {
      history,
      chartMin,
      chartMax,
      width,
      height,
      padding,
      plotWidth,
      plotHeight,
      xStep,
      yTicks,
      xTicks,
      seriesLines,
    };
  }, [selectedBatchHistory, visibleSensors]);

  async function handleGeneratePrediction() {
    if (dailyLimitActive && predictionAvailability?.prediction) {
      setPrediction({
        ...predictionAvailability.prediction,
        message: predictionAvailability.message,
        dailyLimitReached: true,
        nextPredictionAt: predictionAvailability.nextPredictionAt,
      });
      setPredictionError(null);
      setPredictionModalOpen(true);
      return;
    }

    try {
      setLoading(true);
      setPredictionError(null);
      setPrediction(null);
      setPredictionModalOpen(true);

      const response = await generateAIPrediction(batchId || null);

      setPrediction(response);
      if (response.nextPredictionAt) {
        setPredictionAvailability({
          canGenerate: false,
          message: response.message,
          nextPredictionAt: response.nextPredictionAt,
          prediction: response,
        });
      }
    } catch (error) {
      setPredictionError(error.message || 'AI prediction is currently unavailable.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Layout
      user={user}
      title="AI Prediction"
      subtitle="Compost readiness prediction based on sensor readings and actuator logs"
      online={online}
    >
      <div className="prediction-grid">
        <div className="prediction-card">
          <div className="section-header">
            <div>
              <h2>Generate AI Prediction</h2>
              <p>
                {selectedBatch
                  ? `Selected batch: ${selectedBatch.batchCode} - ${selectedBatch.batchName}`
                  : activeBatch
                    ? `Active batch: ${activeBatch.batchCode} - ${activeBatch.batchName}`
                    : 'Create a compost batch in Settings before generating a prediction.'}
              </p>
            </div>
          </div>

          <div className="prediction-controls">
            <div className="form-group">
              <label>Compost batch</label>
              <select
                value={batchId}
                onChange={(event) => setBatchId(event.target.value)}
              >
                {batches.map((batch) => (
                  <option key={batch.batchId} value={batch.batchId}>
                    {batch.batchCode} - {batch.batchName}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              className="primary-button prediction-generate-button"
              onClick={handleGeneratePrediction}
              disabled={loading || availabilityLoading || !batchId}
            >
              {loading && <span className="button-spinner" aria-hidden="true" />}
              <span>
                {loading
                  ? 'Generating AI Prediction...'
                  : dailyLimitActive
                    ? "View Today's Prediction"
                    : 'Generate Prediction'}
              </span>
            </button>
          </div>

          <div className={`prediction-limit-status ${dailyLimitActive ? 'limit-active' : ''}`}>
            <div>
              <strong>{dailyLimitActive ? 'Daily prediction used' : 'Daily prediction available'}</strong>
              <span>
                {dailyLimitActive
                  ? ' This batch can receive one new AI prediction each day.'
                  : ' One prediction can be generated for the selected batch today.'}
              </span>
            </div>
            {dailyLimitActive && (
              <div className="prediction-countdown" aria-live="polite">
                <span>Next prediction in</span>
                <strong>{formatCountdown(countdownSeconds)}</strong>
              </div>
            )}
          </div>

          {(prediction || predictionError) && !loading && (
            <button
              type="button"
              className="secondary-button prediction-open-button"
              onClick={() => setPredictionModalOpen(true)}
            >
              View Prediction Result
            </button>
          )}

          <div className="chart-frame">
            <div className="chart-header-row">
              <div>
                <h3>Sensor Trend Chart</h3>
                <p>
                  Review the latest sensor history and track how actuator-set thresholds influence compost conditions.
                </p>
              </div>
              <div className="legend-toggle-row">
                {SENSOR_SERIES.map((series) => (
                  <button
                    key={series.id}
                    type="button"
                    className={`series-toggle ${activeSeries[series.id] ? 'active' : ''}`}
                    onClick={() => setActiveSeries((prev) => ({ ...prev, [series.id]: !prev[series.id] }))}
                    style={{ borderColor: series.color }}
                  >
                    <span
                      style={{
                        width: 12,
                        height: 12,
                        borderRadius: 9999,
                        background: series.color,
                        display: 'inline-block',
                        marginRight: 8,
                      }}
                    />
                    {series.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="sensor-chart-wrapper">
              {historyLoading ? (
                <div className="empty-state">Loading historical sensor readings...</div>
              ) : chartData.history.length === 0 ? (
                <div className="empty-state">No sensor history available yet.</div>
              ) : (
                <svg className="sensor-chart" viewBox={`0 0 ${chartData.width} ${chartData.height}`}>
                  <rect x="0" y="0" width="100%" height="100%" fill="transparent" />

                  <line
                    x1={chartData.padding}
                    x2={chartData.padding}
                    y1={chartData.padding}
                    y2={chartData.height - chartData.padding}
                    className="chart-axis-line"
                  />
                  <line
                    x1={chartData.padding}
                    x2={chartData.width - chartData.padding}
                    y1={chartData.height - chartData.padding}
                    y2={chartData.height - chartData.padding}
                    className="chart-axis-line"
                  />

                  {chartData.yTicks.map((tick) => (
                    <g key={`y-${tick.value}`}>
                      <line
                        x1={chartData.padding}
                        x2={chartData.width - chartData.padding}
                        y1={tick.y}
                        y2={tick.y}
                        stroke="rgba(148, 163, 184, 0.12)"
                        strokeWidth="1"
                      />
                      <text
                        x={chartData.padding - 12}
                        y={tick.y + 5}
                        className="chart-axis-label"
                        textAnchor="end"
                      >
                        {formatAxisValue(tick.value)}
                      </text>
                    </g>
                  ))}

                  {chartData.xTicks.map((tick, index) => {
                    const label = formatAxisTime(tick.reading.createdAt);
                    return (
                      <g key={`${tick.reading.readingId || tick.reading.createdAt}-${index}`}>
                        <line
                          x1={tick.x}
                          x2={tick.x}
                          y1={chartData.height - chartData.padding}
                          y2={chartData.height - chartData.padding + 8}
                          className="chart-axis-line"
                        />
                        <text
                          x={tick.x}
                          y={chartData.height - chartData.padding + 26}
                          className="chart-axis-label"
                          textAnchor="middle"
                        >
                          <tspan x={tick.x}>{label.time}</tspan>
                          <tspan x={tick.x} dy="18">{label.date}</tspan>
                        </text>
                      </g>
                    );
                  })}

                  {chartData.seriesLines.map(({ series, path }) => (
                    <path
                      key={series.id}
                      d={`M${path}`}
                      fill="none"
                      stroke={series.color}
                      strokeWidth="3"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  ))}

                  {chartData.seriesLines.flatMap(({ series, points }) =>
                    points.map((point, index) => (
                      <circle
                        key={`${series.id}-${index}`}
                        cx={point.x}
                        cy={point.y}
                        r="4"
                        fill={series.color}
                        stroke="#0f172a"
                        strokeWidth="2"
                        onMouseEnter={() => setHoverInfo({ series, point })}
                        onMouseLeave={() => setHoverInfo(null)}
                      />
                    ))
                  )}
                </svg>
              )}

              {hoverInfo && (
                <div className="chart-tooltip">
                  <div className="tooltip-row">
                    <strong>{hoverInfo.series.label}</strong>
                    <span>
                      {formatValue(hoverInfo.point.value)} {hoverInfo.series.unit}
                    </span>
                  </div>
                  <div className="tooltip-row">
                    <span>Timestamp</span>
                    <span>{new Date(hoverInfo.point.reading.createdAt).toLocaleString()}</span>
                  </div>
                  <div className="tooltip-row">
                    <span>Status</span>
                    <span>
                      {formatStatus(hoverInfo.point.reading[`${hoverInfo.series.id}Status`] || null)}
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

      </div>

      {predictionModalOpen && (
        <div className="prediction-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="prediction-modal-title">
          <div className="prediction-modal">
            <div className="prediction-modal-header">
              <h3 id="prediction-modal-title">AI Prediction Result</h3>
              <button
                type="button"
                className="prediction-modal-close"
                onClick={() => setPredictionModalOpen(false)}
                aria-label="Close prediction result"
              >
                x
              </button>
            </div>

            {!prediction && !loading && !predictionError && (
              <p>
                No prediction generated yet. Click the generate button to request
                an AI prediction from the Spring Boot backend.
              </p>
            )}

            {loading && (
              <div className="prediction-loading-state" role="status" aria-live="polite">
                <span className="prediction-loading-spinner" aria-hidden="true" />
                <div>
                  <h4>Generating prediction</h4>
                  <p>
                    Please wait while the system analyzes the compost batch data using
                    the AI prediction service.
                  </p>
                </div>
              </div>
            )}

            {predictionError && (
              <div className="error-box">
                <strong>Error:</strong> {predictionError}
              </div>
            )}

            {prediction && (
              <>
                {prediction.message && (
                  <div className={`prediction-message ${prediction.dailyLimitReached ? 'limit-message' : ''}`}>
                    <strong>{prediction.dailyLimitReached ? 'Prediction limit: ' : ''}</strong>
                    {prediction.message}
                    {prediction.dailyLimitReached && countdownSeconds > 0 && (
                      <span> Next prediction available in {formatCountdown(countdownSeconds)}.</span>
                    )}
                  </div>
                )}
                <div className="prediction-result-row">
                  <strong>Status:</strong>
                  <span>{prediction.success ? 'Success' : 'Failed'}</span>
                </div>

                <div className="prediction-result-row">
                  <strong>Predicted Condition:</strong>
                  <span>{prediction.predictedCondition || 'Not available'}</span>
                </div>

                <div className="prediction-result-row">
                  <strong>Estimated Ready Date:</strong>
                  <span>{prediction.estimatedReadyDate || 'Not available'}</span>
                </div>

                <div className="prediction-result-row">
                  <strong>Estimated Days Remaining:</strong>
                  <span>
                    {prediction.estimatedDaysRemaining !== null &&
                    prediction.estimatedDaysRemaining !== undefined
                      ? `${prediction.estimatedDaysRemaining} day/s`
                      : 'Not available'}
                  </span>
                </div>

                <div className="prediction-result-row">
                  <strong>Confidence Score:</strong>
                  <span>
                    {prediction.confidenceScore !== null &&
                    prediction.confidenceScore !== undefined
                      ? prediction.confidenceScore
                      : 'Not available'}
                  </span>
                </div>

                <hr />

                <h4>Prediction Summary</h4>
                <p>{prediction.predictionSummary || 'No prediction summary available.'}</p>

                <h4>Trend Summary</h4>
                <p>{prediction.trendSummary || 'No trend summary available.'}</p>

                <h4>Recommendation</h4>
                <p>{prediction.recommendation || 'No recommendation available.'}</p>
              </>
            )}
          </div>
        </div>
      )}
    </Layout>
  );
}

export default Prediction;
