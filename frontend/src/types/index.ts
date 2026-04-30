// ─── Domain enums ────────────────────────────────────────────────────────────

export type AlertType =
  | 'CRIME'
  | 'FIRE'
  | 'ACCIDENT'
  | 'MEDICAL_EMERGENCY'
  | 'NATURAL_DISASTER'
  | 'SUSPICIOUS_ACTIVITY'
  | 'TRAFFIC'
  | 'HAZARD'
  | 'MISSING_PERSON'
  | 'OTHER'

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertStatus = 'ACTIVE' | 'RESOLVED'
export type VoteType = 'UPVOTE' | 'DOWNVOTE' | 'CONFIRM'
export type Role = 'USER' | 'ADMIN'

// ─── Domain models ───────────────────────────────────────────────────────────

export interface Alert {
  id: string
  title: string
  description: string
  type: AlertType
  severity: AlertSeverity
  latitude: number
  longitude: number
  createdAt: string
  status: AlertStatus
  reportedBy: string
}

export interface AlertWithStats extends Alert {
  upvotes: number
  downvotes: number
  confirmations: number
  credibilityScore: number
}

export interface User {
  id: string
  email: string
  username: string
  role: Role
}

export interface VoteStats {
  upvotes: number
  downvotes: number
  confirmations: number
  credibilityScore: number
}

// ─── API request/response types ───────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  username: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface CreateAlertRequest {
  title: string
  description: string
  type: AlertType
  severity: AlertSeverity
  latitude: number
  longitude: number
}

export interface VoteRequest {
  voteType: VoteType
}

// ─── WebSocket ────────────────────────────────────────────────────────────────

export interface WsNewAlertFrame {
  type: 'NEW_ALERT'
  alert: Alert
}

export type WsFrame = WsNewAlertFrame

// ─── UI helpers ──────────────────────────────────────────────────────────────

export const ALERT_TYPE_LABELS: Record<AlertType, string> = {
  CRIME: 'Crime',
  FIRE: 'Fire',
  ACCIDENT: 'Accident',
  MEDICAL_EMERGENCY: 'Medical Emergency',
  NATURAL_DISASTER: 'Natural Disaster',
  SUSPICIOUS_ACTIVITY: 'Suspicious Activity',
  TRAFFIC: 'Traffic',
  HAZARD: 'Hazard',
  MISSING_PERSON: 'Missing Person',
  OTHER: 'Other',
}

export const ALERT_TYPE_ICONS: Record<AlertType, string> = {
  CRIME: '🔫',
  FIRE: '🔥',
  ACCIDENT: '💥',
  MEDICAL_EMERGENCY: '🚑',
  NATURAL_DISASTER: '🌪️',
  SUSPICIOUS_ACTIVITY: '👁️',
  TRAFFIC: '🚗',
  HAZARD: '⚠️',
  MISSING_PERSON: '🔍',
  OTHER: '📍',
}

export const SEVERITY_COLORS: Record<AlertSeverity, string> = {
  LOW: 'text-green-400 bg-green-400/10 border-green-400/30',
  MEDIUM: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/30',
  HIGH: 'text-orange-400 bg-orange-400/10 border-orange-400/30',
  CRITICAL: 'text-red-400 bg-red-400/10 border-red-400/30',
}

export const SEVERITY_MARKER_COLORS: Record<AlertSeverity, string> = {
  LOW: '#22c55e',
  MEDIUM: '#f59e0b',
  HIGH: '#f97316',
  CRITICAL: '#ef4444',
}
