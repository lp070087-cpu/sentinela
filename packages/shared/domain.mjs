/** Domain contracts shared by local backend and external clients. No credentials. */
export const DEVICE_STATES = Object.freeze(['DISABLED', 'ACTIVE', 'EMERGENCY', 'LOST', 'RECOVERING', 'RESOLVED']);
export const COMMAND_TYPES = Object.freeze(['LOCK', 'LOST', 'RECOVER', 'ALARM', 'STOP_ALARM']);
export const COMMAND_STATUSES = Object.freeze(['QUEUED', 'LEASED', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED']);
export const PLANS = Object.freeze({ ESSENTIAL: { deviceLimit: 1 }, PLUS: { deviceLimit: 1 }, FAMILY: { deviceLimit: 5 } });

export function validateState(state) {
  if (typeof state !== 'string' || !DEVICE_STATES.includes(state)) throw new Error('Estado do dispositivo inválido.');
  return state;
}
export function validateCommand(type) {
  if (typeof type !== 'string' || !COMMAND_TYPES.includes(type)) throw new Error('Comando inválido.');
  return type;
}
export function validateLocation(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('Localização inválida.');
  const { latitude, longitude, accuracy, capturedAt } = body;
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) throw new Error('Latitude inválida.');
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) throw new Error('Longitude inválida.');
  if (!Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100000) throw new Error('Precisão inválida.');
  if (!(typeof capturedAt === 'number' && Number.isFinite(capturedAt)) && !(typeof capturedAt === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(capturedAt))) throw new Error('Horário de captura inválido.');
  const time = typeof capturedAt === 'number' ? capturedAt : Date.parse(capturedAt);
  if (!Number.isFinite(time) || time > Date.now() + 5 * 60 * 1000) throw new Error('Horário de captura inválido ou no futuro.');
  return { latitude, longitude, accuracy, capturedAt: time };
}

const transitions = Object.freeze({
  DISABLED: ['ACTIVE', 'EMERGENCY', 'LOST'],
  ACTIVE: ['DISABLED', 'EMERGENCY', 'LOST'],
  EMERGENCY: ['RECOVERING', 'LOST', 'RESOLVED'],
  LOST: ['EMERGENCY', 'RECOVERING', 'RESOLVED'],
  RECOVERING: ['EMERGENCY', 'LOST', 'RESOLVED'],
  RESOLVED: ['ACTIVE', 'DISABLED', 'EMERGENCY', 'LOST']
});
export function canTransition(from, to) {
  validateState(from); validateState(to);
  return from === to || transitions[from].includes(to);
}
export function assertTransition(from, to) {
  if (!canTransition(from, to)) throw new Error(`Transição não permitida: ${from} → ${to}.`);
  return to;
}
/** A queued remote command never proves that the native action ran. */
export function commandCanConfirmExecution(command) {
  return command?.status === 'SUCCEEDED' && typeof command?.ackedAt === 'number' && Number.isFinite(command.ackedAt);
}
/** Disconnects and plan expiry do not implicitly end an active emergency. */
export function isEmergencyState(state) {
  validateState(state);
  return ['EMERGENCY', 'LOST', 'RECOVERING'].includes(state);
}
