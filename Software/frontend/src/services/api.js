const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
).replace(/\/$/, '');
const AUTH_SESSION_KEY = 'compostAuthSession';

class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.status = status;
  }
}

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
      throw new ApiError('This is taking longer than expected. Please try again.');
    }

    throw new ApiError("We couldn't complete your request. Check your connection and try again.");
  } finally {
    window.clearTimeout(timeoutId);
  }

  if (response.status === 204) {
    return null;
  }

  let data = null;

  // A stale request must not clear a newer login, and OTP failures are not session failures.
  if (response.status === 401 && (path === '/auth/session' || !path.startsWith('/auth/'))
      && getStoredAuthSession()?.sessionToken === token) {
    clearStoredAuthSession();
  }

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new ApiError('Something went wrong while processing your request. Please try again.', response.status);
  }

  if (!response.ok || data?.success === false) {
    throw new ApiError(data?.message || 'Request failed.', response.status);
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

let sessionValidation = null;

export function validateSession() {
  const token = getStoredAuthSession()?.sessionToken;
  if (!token) return Promise.reject(new ApiError('Please sign in again.', 401));
  if (sessionValidation?.token === token) return sessionValidation.promise;

  const pending = { token };
  pending.promise = request('/auth/session', { method: 'GET', cache: 'no-store' })
    .then((data) => {
      if (getStoredAuthSession()?.sessionToken !== token) {
        throw new ApiError('Your session changed. Please try again.');
      }
      if (!data?.user || data.sessionToken !== token || !data.expiresAt) {
        throw new ApiError("We couldn't check your session right now. Please try again shortly.");
      }
      return storeAuthSession(data);
    })
    .finally(() => {
      if (sessionValidation === pending) sessionValidation = null;
    });
  sessionValidation = pending;
  return pending.promise;
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
