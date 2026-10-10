import { useEffect, useMemo, useState } from 'react';
import Layout from '../components/Layout.jsx';
import {
  getActuatorLogs,
  getSensorConnectionLogs,
  getSensorReadings,
} from '../services/api.js';

const PAGE_SIZE_OPTIONS = [10, 25, 50];
const CONNECTION_PAGE_SIZE = 10;

function PaginationControls({
  page,
  pageSize,
  totalItems,
  totalPages,
  onPageChange,
  onPageSizeChange,
}) {
  const safePage = Math.min(page, totalPages);
  const startItem = totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const endItem = Math.min(totalItems, safePage * pageSize);

  return (
    <div className="logs-pagination">
      <div className="logs-page-summary">
        Showing {startItem}-{endItem} of {totalItems}
      </div>

      <div className="logs-page-controls">
        {onPageSizeChange && <label>
          Rows
          <select
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>}

        <button
          type="button"
          onClick={() => onPageChange(1)}
          disabled={safePage === 1}
        >
          First
        </button>
        <button
          type="button"
          onClick={() => onPageChange(safePage - 1)}
          disabled={safePage === 1}
        >
          Previous
        </button>
        <span>
          Page {safePage} of {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(safePage + 1)}
          disabled={safePage === totalPages}
        >
          Next
        </button>
        <button
          type="button"
          onClick={() => onPageChange(totalPages)}
          disabled={safePage === totalPages}
        >
          Last
        </button>
      </div>
    </div>
  );
}

function Logs({ user, online, setOnline }) {
  const [sensorReadings, setSensorReadings] = useState([]);
  const [actuatorLogs, setActuatorLogs] = useState([]);
  const [connectionLogs, setConnectionLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [sensorPage, setSensorPage] = useState(1);
  const [actuatorPage, setActuatorPage] = useState(1);
  const [connectionPage, setConnectionPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    async function loadLogs() {
      try {
        const [readings, logs, connections] = await Promise.all([
          getSensorReadings(),
          getActuatorLogs(),
          getSensorConnectionLogs(),
        ]);

        setSensorReadings(Array.isArray(readings) ? readings : []);
        setActuatorLogs(Array.isArray(logs) ? logs : []);
        setConnectionLogs(Array.isArray(connections) ? connections : []);
      } catch {
        setSensorReadings([]);
        setActuatorLogs([]);
        setConnectionLogs([]);
      } finally {
        setLoading(false);
      }
    }

    loadLogs();

    const connectionInterval = setInterval(async () => {
      try {
        const connections = await getSensorConnectionLogs();
        setConnectionLogs(Array.isArray(connections) ? connections : []);
      } catch {
        // Keep the latest connection history while the backend is unavailable.
      }
    }, 5000);

    return () => clearInterval(connectionInterval);
  }, []);

  useEffect(() => {
    setSensorPage(1);
    setActuatorPage(1);
  }, [statusFilter, searchTerm, pageSize]);

  const normalizedSearch = searchTerm.trim().toLowerCase();

  const filteredReadings = useMemo(() => {
    return sensorReadings.filter((reading) => {
      const statusMatch =
        statusFilter === 'ALL' ||
        reading.moistureStatus === statusFilter ||
        reading.gasStatus === statusFilter ||
        reading.temperatureStatus === statusFilter ||
        reading.humidityStatus === statusFilter;
      if (!statusMatch) return false;

      if (!normalizedSearch) return true;

      const timestamp = new Date(reading.createdAt).toLocaleString().toLowerCase();
      return (
        timestamp.includes(normalizedSearch) ||
        `${reading.temperatureC}`.includes(normalizedSearch) ||
        `${reading.moistureLevel}`.includes(normalizedSearch) ||
        `${reading.gasLevel}`.includes(normalizedSearch) ||
        `${reading.humidityLevel}`.includes(normalizedSearch)
      );
    });
  }, [sensorReadings, statusFilter, normalizedSearch]);

  const filteredActuatorLogs = useMemo(() => {
    if (!normalizedSearch) return actuatorLogs;

    return actuatorLogs.filter((log) => {
      const timestamp = new Date(log.createdAt).toLocaleString().toLowerCase();
      return (
        timestamp.includes(normalizedSearch) ||
        `${log.actuatorType}`.toLowerCase().includes(normalizedSearch) ||
        `${log.triggerSource}`.toLowerCase().includes(normalizedSearch) ||
        `${log.triggerValue}`.includes(normalizedSearch) ||
        `${log.thresholdValue}`.includes(normalizedSearch)
      );
    });
  }, [actuatorLogs, normalizedSearch]);

  const sensorTotalPages = Math.max(1, Math.ceil(filteredReadings.length / pageSize));
  const actuatorTotalPages = Math.max(1, Math.ceil(filteredActuatorLogs.length / pageSize));
  const safeSensorPage = Math.min(sensorPage, sensorTotalPages);
  const safeActuatorPage = Math.min(actuatorPage, actuatorTotalPages);
  const connectionTotalPages = Math.max(1, Math.ceil(connectionLogs.length / CONNECTION_PAGE_SIZE));
  const safeConnectionPage = Math.min(connectionPage, connectionTotalPages);
  const pagedConnectionLogs = connectionLogs.slice(
    (safeConnectionPage - 1) * CONNECTION_PAGE_SIZE,
    safeConnectionPage * CONNECTION_PAGE_SIZE
  );

  const pagedReadings = useMemo(() => {
    const start = (safeSensorPage - 1) * pageSize;
    return filteredReadings.slice(start, start + pageSize);
  }, [filteredReadings, pageSize, safeSensorPage]);

  const pagedActuatorLogs = useMemo(() => {
    const start = (safeActuatorPage - 1) * pageSize;
    return filteredActuatorLogs.slice(start, start + pageSize);
  }, [filteredActuatorLogs, pageSize, safeActuatorPage]);

  const formatStatus = (status) => {
    if (!status) return 'Unknown';
    if (status === 'NORMAL') return 'Optimal';
    return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
  };

  const formatNumber = (value) => {
    if (value === null || value === undefined) return '--';
    return Number(value).toFixed(1);
  };

  const formatDateTime = (value) => {
    if (!value) return '--';
    return new Date(value).toLocaleString();
  };

  return (
    <Layout
      user={user}
      title="Logs / History"
      subtitle="View sensor readings and actuator pulses from the database"
      online={online}
      setOnline={setOnline}
    >
      <div className="logs-panel">
        <div className="logs-toolbar">
          <div className="logs-filter">
            <label htmlFor="statusFilter">Sensor status</label>
            <select
              id="statusFilter"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              <option value="ALL">All</option>
              <option value="LOW">Low</option>
              <option value="NORMAL">Optimal</option>
              <option value="HIGH">High</option>
            </select>
          </div>
          <div className="logs-search">
            <label htmlFor="searchTerm">Search</label>
            <input
              id="searchTerm"
              type="text"
              placeholder="Search timestamp, sensor, or actuator"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
          </div>
        </div>

        <h3 className="logs-section-title">Sensor Reading History</h3>
        <table className="logs-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Temperature (°C)</th>
              <th>Moisture (%)</th>
              <th>Gas (%)</th>
              <th>Humidity (%)</th>
              <th>Moisture Status</th>
              <th>Gas Status</th>
              <th>Temperature Status</th>
              <th>Humidity Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="9" className="empty-state">
                  Loading sensor readings...
                </td>
              </tr>
            ) : filteredReadings.length === 0 ? (
              <tr>
                <td colSpan="9" className="empty-state">
                  No sensor readings match the selected filter.
                </td>
              </tr>
            ) : (
              pagedReadings.map((reading) => (
                <tr key={reading.readingId}>
                  <td>{formatDateTime(reading.createdAt)}</td>
                  <td>{formatNumber(reading.temperatureC)}</td>
                  <td>{formatNumber(reading.moistureLevel)}</td>
                  <td>{formatNumber(reading.gasLevel)}</td>
                  <td>{formatNumber(reading.humidityLevel)}</td>
                  <td className={`status-${reading.moistureStatus?.toLowerCase() || 'unknown'}`}>
                    {formatStatus(reading.moistureStatus)}
                  </td>
                  <td className={`status-${reading.gasStatus?.toLowerCase() || 'unknown'}`}>
                    {formatStatus(reading.gasStatus)}
                  </td>
                  <td className={`status-${reading.temperatureStatus?.toLowerCase() || 'unknown'}`}>
                    {formatStatus(reading.temperatureStatus)}
                  </td>
                  <td className={`status-${reading.humidityStatus?.toLowerCase() || 'unknown'}`}>
                    {formatStatus(reading.humidityStatus)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        {!loading && filteredReadings.length > 0 && (
          <PaginationControls
            page={safeSensorPage}
            pageSize={pageSize}
            totalItems={filteredReadings.length}
            totalPages={sensorTotalPages}
            onPageChange={setSensorPage}
            onPageSizeChange={setPageSize}
          />
        )}

        <h3 className="logs-section-title">ESP32 Connection History</h3>
        <table className="logs-table connection-logs-table">
          <thead>
            <tr>
              <th>Event Timestamp</th>
              <th>Connection Event</th>
              <th>Sensor Status</th>
              <th>Last Sensor Reading</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="4" className="empty-state">Loading connection history...</td>
              </tr>
            ) : connectionLogs.length === 0 ? (
              <tr>
                <td colSpan="4" className="empty-state">No ESP32 connection events have been logged.</td>
              </tr>
            ) : (
              pagedConnectionLogs.map((log) => (
                <tr key={log.logId}>
                  <td>{formatDateTime(log.occurredAt)}</td>
                  <td>{log.eventType === 'RECONNECTED' ? 'Connection regained' : 'Connection lost'}</td>
                  <td className="status-na">{log.sensorStatus || 'NA'}</td>
                  <td>{formatDateTime(log.lastReadingAt)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>

        {!loading && connectionLogs.length > 0 && (
          <PaginationControls
            page={safeConnectionPage}
            pageSize={CONNECTION_PAGE_SIZE}
            totalItems={connectionLogs.length}
            totalPages={connectionTotalPages}
            onPageChange={setConnectionPage}
          />
        )}

        <h3 className="logs-section-title">Actuator Log History</h3>
        <table className="logs-table">
          <thead>
            <tr>
              <th>Started</th>
              <th>Ended</th>
              <th>Actuator Type</th>
              <th>Status</th>
              <th>Trigger Source</th>
              <th>Trigger Value</th>
              <th>Threshold Value</th>
              <th>Duration</th>
              <th>Reading ID</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="9" className="empty-state">
                  Loading actuator logs...
                </td>
              </tr>
            ) : filteredActuatorLogs.length === 0 ? (
              <tr>
                <td colSpan="9" className="empty-state">
                  No actuator logs match the current search.
                </td>
              </tr>
            ) : (
              pagedActuatorLogs.map((log) => (
                <tr key={log.logId}>
                  <td>{formatDateTime(log.startedAt)}</td>
                  <td>{formatDateTime(log.endedAt)}</td>
                  <td>{log.actuatorType}</td>
                  <td>{log.status}</td>
                  <td>{log.triggerSource}</td>
                  <td>{formatNumber(log.triggerValue)}</td>
                  <td>{formatNumber(log.thresholdValue)}</td>
                  <td>{log.durationSeconds ?? '--'}s</td>
                  <td>{log.readingId ?? '--'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        {!loading && filteredActuatorLogs.length > 0 && (
          <PaginationControls
            page={safeActuatorPage}
            pageSize={pageSize}
            totalItems={filteredActuatorLogs.length}
            totalPages={actuatorTotalPages}
            onPageChange={setActuatorPage}
            onPageSizeChange={setPageSize}
          />
        )}
      </div>
    </Layout>
  );
}

export default Logs;
