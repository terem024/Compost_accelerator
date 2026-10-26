import test from 'node:test';
import assert from 'node:assert/strict';
import { presentPrediction } from './predictionPresentation.js';

const today = new Date('2026-10-26T01:00:00Z');
test('a past date and zero days do not present a batch as ready', () => {
  const result = presentPrediction({ estimatedReadyDate: '2026-09-12', estimatedDaysRemaining: 0,
    predictionSummary: 'Ready for use.', recommendation: 'Use now.' }, today);
  assert.equal(result.usable, false);
  assert.ok(result.warning);
  assert.doesNotMatch(result.summary, /Ready for use/);
  assert.notEqual(result.recommendation, 'Use now.');
});
test('positive future estimates remain visible', () => {
  const result = presentPrediction({ estimatedReadyDate: '2026-11-01', estimatedDaysRemaining: 6 }, today);
  assert.equal(result.usable, true);
  assert.equal(result.warning, '');
});
test('zero days with no date is also guarded', () => {
  const result = presentPrediction({ estimatedReadyDate: null, estimatedDaysRemaining: 0,
    predictionSummary: 'Ready for use.' }, today);
  assert.equal(result.usable, false);
  assert.doesNotMatch(result.summary, /Ready for use/);
});
test('date comparison uses Manila rather than the browser timezone', () => {
  const result = presentPrediction({ estimatedReadyDate: '2026-10-26', estimatedDaysRemaining: 1 },
    new Date('2026-10-25T17:00:00Z'));
  assert.equal(result.usable, false);
});
