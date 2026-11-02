import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLocation, validateState, validateCommand, canTransition, assertTransition, commandCanConfirmExecution, isEmergencyState } from './domain.mjs';

test('location validates limits and normalizes timestamp', () => {
  const fix = validateLocation({latitude: -8.05, longitude: -34.9, accuracy: 12, capturedAt: '2026-01-01T12:00:00Z'});
  assert.equal(fix.capturedAt, Date.parse('2026-01-01T12:00:00.000Z'));
  assert.equal(validateLocation({...fix, capturedAt: fix.capturedAt}).capturedAt, fix.capturedAt);
  assert.throws(() => validateLocation({...fix, latitude: 90.1}));
  assert.throws(() => validateLocation({...fix, longitude: NaN}));
  assert.throws(() => validateLocation({...fix, accuracy: -1}));
  assert.throws(() => validateLocation({...fix, capturedAt: new Date(Date.now()+3600000).toISOString()}));
  assert.throws(() => validateLocation({...fix, latitude: '-8.05'}));
});
test('state and commands reject unknown input', () => {
  assert.equal(validateState('EMERGENCY'), 'EMERGENCY');
  assert.equal(validateCommand('STOP_ALARM'), 'STOP_ALARM');
  assert.throws(() => validateState('UNLOCKED'));
  assert.throws(() => validateCommand('BYPASS_PIN'));
});
test('emergency cannot be silently switched off', () => {
  assert.equal(canTransition('ACTIVE', 'EMERGENCY'), true);
  assert.equal(canTransition('EMERGENCY', 'DISABLED'), false);
  assert.equal(assertTransition('EMERGENCY', 'RECOVERING'), 'RECOVERING');
  assert.equal(assertTransition('RECOVERING', 'RESOLVED'), 'RESOLVED');
  assert.throws(() => assertTransition('LOST', 'DISABLED'));
  assert.equal(isEmergencyState('LOST'), true);
});
test('queued or leased command is not proof of native execution', () => {
  assert.equal(commandCanConfirmExecution({status:'QUEUED'}), false);
  assert.equal(commandCanConfirmExecution({status:'LEASED'}), false);
  assert.equal(commandCanConfirmExecution({status:'SUCCEEDED'}), false);
  assert.equal(commandCanConfirmExecution({status:'SUCCEEDED', ackedAt:NaN}), false);
  assert.equal(commandCanConfirmExecution({status:'SUCCEEDED', ackedAt:Date.now()}), true);
});
