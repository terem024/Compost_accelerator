const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
).replace(/\/$/, '');
const AUTH_SESSION_KEY = 'compostAuthSession';

export function getStoredAuthSession() {
  try {
    const savedSession = localStorage.getItem(AUTH_SESSION_KEY);
    const session = savedSession ? JSON.parse(savedSession) : null;

    if (!session?.sessionToken || !session?.user) {
      return null;
    }

    if (session.expiresAt && new Date(session.expiresAt) <= new Date()) {
      clearStoredAuthSession();
      return null;
    }

    return session;
  } catch {
    clearStoredAuthSession();
    return null;
  }
}

export function storeAuthSession(data) {
  const session = {
    user: data.user,
    sessionToken: data.sessionToken,
    expiresAt: data.expiresAt,
  };

  localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session));
  localStorage.removeItem('compostUser');
  return session;
}

export function clearStoredAuthSession() {
  localStorage.removeItem(AUTH_SESSION_KEY);
  localStorage.removeItem('compostUser');
}

async function request(path, options = {}) {
  const { timeoutMs = 20000, ...fetchOptions } = options;
  const storedSession = getStoredAuthSession();
  const token = storedSession?.sessionToken;
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);

  let response;
  let text;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...fetchOptions,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(fetchOptions.headers || {}),
      },
      signal: controller.signal,
    });
    text = response.status === 204 ? '' : await response.text();
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error('This is taking longer than expected. Please try again.');
    }

    throw new Error("We couldn't complete your request. Check your connection and try again.");
  } finally {
    window.clearTimeout(timeoutId);
  }

  if (response.status === 204) {
    return null;
  }

  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new Error('Something went wrong while processing your request. Please try again.');
  }

  if (!response.ok || data?.success === false) {
    if (response.status === 401) {
      clearStoredAuthSession();
    }

    throw new Error(data?.message || 'Request failed.');
  }

  return data;
}

// ==========================
// AUTHENTICATION
// ==========================

export async function loginUser(credentials) {
  const data = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      email: credentials.email.trim(),
      password: credentials.password,
    }),
  });

  return storeAuthSession(data);
}

export async function registerUser(profile) {
  const data = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      name: profile.name.trim(),
      email: profile.email.trim(),
      password: profile.password,
      confirmPassword: profile.confirmPassword,
    }),
  });

  return storeAuthSession(data);
}

export async function sendOtpEmail(email) {
  return request('/auth/send-otp', {
    method: 'POST',
    timeoutMs: 45000,
    body: JSON.stringify({
      email: email.trim(),
    }),
  });
}

export async function verifyOtp(email, otp) {
  return request('/auth/verify-otp', {
    method: 'POST',
    body: JSON.stringify({
      email: email.trim(),
      otp: otp.trim(),
    }),
  });
}

export async function validateSession() {
  const data = await request('/auth/session', {
    method: 'GET',
  });

  return storeAuthSession(data);
}

export async function logoutUser() {
  try {
    await request('/auth/logout', {
      method: 'POST',
    });
  } finally {
    clearStoredAuthSession();
  }
}

export async function forgotPassword(email) {
  return request('/auth/forgot-password', {
    method: 'POST',
    timeoutMs: 45000,
    body: JSON.stringify({
      email: email.trim(),
    }),
  });
}

export async function resetPassword(token, newPassword, confirmPassword) {
  return request('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({
      token,
      newPassword,
      confirmPassword,
    }),
  });
}

// ==========================
// THRESHOLD SETTINGS
// ==========================

export async function getThresholdSettings() {
  return request('/settings/thresholds', {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function saveThresholdSettings(thresholds) {
  return request('/settings/thresholds', {
    method: 'PUT',
    body: JSON.stringify(thresholds),
  });
}

// ==========================
// SENSOR READINGS
// ==========================

export async function saveSensorReading(reading) {
  return request('/sensor-readings', {
    method: 'POST',
    body: JSON.stringify(reading),
  });
}

export async function getLatestSensorReading() {
  return request('/sensor-readings/latest', {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function getSensorReadings() {
  return request('/sensor-readings', {
    method: 'GET',
  });
}

export async function getSensorConnectionStatus() {
  return request('/sensor-connection/status', {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function getSensorConnectionLogs() {
  return request('/sensor-connection/logs', {
    method: 'GET',
    cache: 'no-store',
  });
}

// Subscribe to server-sent events for live sensor readings.
// onMessage is a callback that receives the parsed sensor reading object.
export function subscribeSensorStream(onMessage, onError) {
  const url = `${API_BASE_URL}/sensor-readings/stream`;
  const es = new EventSource(url);

  es.addEventListener('sensor-reading', (ev) => {
    try {
      const data = JSON.parse(ev.data);
      onMessage && onMessage(data);
    } catch (err) {
      console.error('Failed to parse SSE message', err);
    }
  });

  es.onerror = (err) => {
    if (onError) onError(err);
  };

  return es;
}

// REMOVED: Sensor simulation endpoints disabled - use real sensor data from IoT devices
// Real sensors should POST to /api/sensor-readings with actual measurements

// ==========================
// ACTUATOR STATUS
// ==========================

export async function getActuatorStatus() {
  return request('/actuator-status', {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function getActuatorLogs() {
  return request('/actuator-logs', {
    method: 'GET',
  });
}

// ==========================
// COMPOST BATCHES
// ==========================

export async function getActiveCompostBatch() {
  return request('/compost-batches/active', {
    method: 'GET',
  });
}

export async function getCompostBatches() {
  return request('/compost-batches', {
    method: 'GET',
  });
}

export async function createCompostBatch(batch) {
  return request('/compost-batches', {
    method: 'POST',
    body: JSON.stringify(batch),
  });
}

export async function updateCompostBatch(batchId, batch) {
  return request(`/compost-batches/${batchId}`, {
    method: 'PUT',
    body: JSON.stringify(batch),
  });
}

export async function setActiveCompostBatch(batchId) {
  return request(`/compost-batches/${batchId}/activate`, {
    method: 'POST',
  });
}

export async function updateCompostBatchStatus(batchId, status) {
  return request(`/compost-batches/${batchId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

// ==========================
// AI PREDICTION
// ==========================

export async function generateAIPrediction(batchId = null) {
  const selectedBatchId = batchId ? Number(batchId) : null;
  const path = selectedBatchId
    ? `/predictions/generate/${selectedBatchId}`
    : '/predictions/generate';

  return request(path, {
    method: 'POST',
    body: JSON.stringify({
      ...(selectedBatchId ? { batchId: selectedBatchId } : {}),
    }),
  });
}

export async function getAIPredictionAvailability(batchId) {
  return request(`/predictions/availability/${Number(batchId)}`);
}
