export function presentPrediction(prediction, now = new Date()) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(now);
  const date = prediction?.estimatedReadyDate;
  const usable = /^\d{4}-\d{2}-\d{2}$/.test(date || '') && date > today
    && Number.isInteger(prediction?.estimatedDaysRemaining) && prediction.estimatedDaysRemaining > 0;
  const invalidSavedEstimate = !usable && (date || prediction?.estimatedDaysRemaining != null);
  return {
    usable,
    warning: prediction?.estimateWarning || (!usable
      ? 'No reliable future date is available. Inspect the compost before marking it ready.' : ''),
    summary: invalidSavedEstimate
      ? 'The saved readiness estimate is outdated or inconsistent. Readiness has not been confirmed.'
      : prediction?.predictionSummary || 'No summary available.',
    recommendation: invalidSavedEstimate
      ? 'Check the compost and sensor readings with the responsible personnel before use.'
      : prediction?.recommendation || 'Continue monitoring and inspect the compost before use.',
  };
}
