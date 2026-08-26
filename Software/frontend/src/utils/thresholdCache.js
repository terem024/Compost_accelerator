// Local cache utilities for threshold settings
// Used as fallback when backend is unavailable

const THRESHOLDS_KEY = 'compostThresholds';

const defaultThresholds = {
  moistureMin: 50,
  gasMax: 60,
  readingIntervalSeconds: 60,
  sprayDurationSeconds: 15,
  fanDurationSeconds: 5,
  sprayCooldownSeconds: 30,
  fanCooldownSeconds: 30,
};

export function getThresholds() {
  const stored = localStorage.getItem(THRESHOLDS_KEY);
  if (stored) {
    try {
      const parsed = JSON.parse(stored);
      const gasMax = Number(parsed.gasMax);
      return {
        ...parsed,
        gasMax: Number.isFinite(gasMax) && gasMax > 100 ? gasMax / 20 : gasMax,
      };
    } catch {
      return defaultThresholds;
    }
  }
  return defaultThresholds;
}

export function saveThresholds(thresholds) {
  localStorage.setItem(THRESHOLDS_KEY, JSON.stringify(thresholds));
}
