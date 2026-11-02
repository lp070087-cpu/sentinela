export type DeviceState = 'DISABLED' | 'ACTIVE' | 'EMERGENCY' | 'LOST' | 'RECOVERING' | 'RESOLVED';
export type CommandType = 'LOCK' | 'LOST' | 'RECOVER' | 'ALARM' | 'STOP_ALARM';
export type CommandStatus = 'QUEUED' | 'LEASED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED' | 'CANCELLED';
export type PlanCode = 'ESSENTIAL' | 'PLUS' | 'FAMILY';
export interface LocationFix { latitude: number; longitude: number; accuracy: number; capturedAt: number }
export interface LocationInput { latitude: number; longitude: number; accuracy: number; capturedAt: string | number }
export interface DeviceEvent { eventId?: string; state: DeviceState; type: string; details?: string; lockResult?: string }
export interface DeviceCommand { id: string; deviceId: string; type: CommandType; status: CommandStatus; payload?: Record<string, unknown>; createdAt: number; expiresAt: number; ackedAt?: number }
export interface RegisteredDevice { device: { id: string; name: string }; deviceToken: string }
export const DEVICE_STATES: readonly DeviceState[];
export const COMMAND_TYPES: readonly CommandType[];
export const COMMAND_STATUSES: readonly CommandStatus[];
export const PLANS: Readonly<Record<PlanCode, {deviceLimit: number}>>;
export function validateState(state: unknown): DeviceState;
export function validateCommand(type: unknown): CommandType;
export function validateLocation(body: unknown): LocationFix;
export function canTransition(from: DeviceState, to: DeviceState): boolean;
export function assertTransition(from: DeviceState, to: DeviceState): DeviceState;
export function commandCanConfirmExecution(command: Partial<DeviceCommand>): boolean;
export function isEmergencyState(state: DeviceState): boolean;
