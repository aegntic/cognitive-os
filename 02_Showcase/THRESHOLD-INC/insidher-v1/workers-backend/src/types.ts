// TypeScript types matching Kotlin contracts in :contracts module

// ThreadState sealed hierarchy (12 variants)
export const ThreadStates = [
  "NEW",
  "GREETING",
  "CONVERSING",
  "DEPOSIT_REQUESTED",
  "DEPOSIT_PENDING",
  "HUMAN_REVIEW",
  "CONFIRMED",
  "ESCALATED",
  "ENDED",
  "STALLED",
  "AI_CHALLENGED",
  "COOLDOWN",
] as const;
export type ThreadState = (typeof ThreadStates)[number];

// Active states (not terminal) for stalled detection
export const ACTIVE_STATES: ThreadState[] = [
  "NEW",
  "GREETING",
  "CONVERSING",
  "DEPOSIT_REQUESTED",
  "DEPOSIT_PENDING",
];

// Terminal states
export const TERMINAL_STATES: ThreadState[] = ["CONFIRMED", "ENDED", "ESCALATED"];

// Valid state transitions
export const VALID_TRANSITIONS: Record<ThreadState, ThreadState[]> = {
  NEW: ["GREETING", "STALLED", "ENDED", "ESCALATED", "COOLDOWN"],
  GREETING: ["CONVERSING", "AI_CHALLENGED", "STALLED", "ENDED", "ESCALATED", "COOLDOWN"],
  CONVERSING: [
    "DEPOSIT_REQUESTED",
    "AI_CHALLENGED",
    "STALLED",
    "ENDED",
    "ESCALATED",
    "COOLDOWN",
  ],
  DEPOSIT_REQUESTED: [
    "DEPOSIT_PENDING",
    "CONVERSING",
    "AI_CHALLENGED",
    "STALLED",
    "ENDED",
    "ESCALATED",
    "COOLDOWN",
  ],
  DEPOSIT_PENDING: [
    "HUMAN_REVIEW",
    "DEPOSIT_REQUESTED",
    "AI_CHALLENGED",
    "STALLED",
    "ENDED",
    "ESCALATED",
    "COOLDOWN",
  ],
  HUMAN_REVIEW: ["CONFIRMED", "ESCALATED", "ENDED"],
  CONFIRMED: ["ENDED"],
  ESCALATED: ["CONVERSING", "ENDED"],
  ENDED: [],
  STALLED: ["CONVERSING", "ENDED", "ESCALATED"],
  AI_CHALLENGED: [
    "NEW",
    "GREETING",
    "CONVERSING",
    "DEPOSIT_REQUESTED",
    "DEPOSIT_PENDING",
    "STALLED",
    "ENDED",
    "ESCALATED",
    "COOLDOWN",
  ],
  COOLDOWN: [
    "NEW",
    "GREETING",
    "CONVERSING",
    "DEPOSIT_REQUESTED",
    "DEPOSIT_PENDING",
    "ENDED",
    "ESCALATED",
  ],
};

// DepositStatus sealed hierarchy
export type DepositStatus = "PENDING" | "RECEIVED" | "VERIFIED" | "FAILED";

export const VALID_DEPOSIT_STATUSES: DepositStatus[] = [
  "PENDING",
  "RECEIVED",
  "VERIFIED",
  "FAILED",
];

// Valid deposit status transitions
export const DEPOSIT_TRANSITIONS: Record<DepositStatus, DepositStatus[]> = {
  PENDING: ["RECEIVED", "FAILED"],
  RECEIVED: ["VERIFIED", "FAILED"],
  VERIFIED: [],
  FAILED: [],
};

// HumanDecision
export type HumanDecision = "APPROVE" | "REJECT" | "ESCALATE";

// SafetyVerdict
export type SafetyVerdictType = "SAFE" | "ESCALATE" | "BLOCK";

// EscalationReasonCodes (19)
export const EscalationReasonCodes = {
  AI_CHALLENGE_DETECTED: "AI_CHALLENGE_DETECTED",
  COERCION_DETECTED: "COERCION_DETECTED",
  EXPLOITATION_RISK: "EXPLOITATION_RISK",
  MINOR_SAFETY_RISK: "MINOR_SAFETY_RISK",
  ILLEGAL_SERVICE_REQUEST: "ILLEGAL_SERVICE_REQUEST",
  HARM_THREAT: "HARM_THREAT",
  PROMPT_INJECTION_DETECTED: "PROMPT_INJECTION_DETECTED",
  JAILBREAK_ATTEMPT: "JAILBREAK_ATTEMPT",
  PERSONA_DEVIATION: "PERSONA_DEVIATION",
  EXCESSIVE_PERSISTENCE: "EXCESSIVE_PERSISTENCE",
  DEPOSIT_DISPUTE: "DEPOSIT_DISPUTE",
  RATE_LIMIT_HIT: "RATE_LIMIT_HIT",
  UNKNOWN_RISK: "UNKNOWN_RISK",
} as const;

export type EscalationReasonCode =
  (typeof EscalationReasonCodes)[keyof typeof EscalationReasonCodes];

// Evidence types for deposits
export type EvidenceType = "STRIPE_WEBHOOK" | "MANUAL_FLAG";

// PersonaProfile
export interface PersonaProfile {
  name: string;
  tone: string;
  vocabulary: string[];
  offerings: string[];
  depositWording: string | null;
  boundaries: string[] | null;
  availabilityPolicy: AvailabilityPolicy;
}

// AvailabilityPolicy
export interface TimeWindow {
  start: string; // "HH:MM"
  end: string; // "HH:MM"
}

export type DayOfWeek =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface AvailabilityPolicy {
  timezone: string;
  weeklyWindows: Partial<Record<DayOfWeek, TimeWindow | null>>;
  dndPeriods: Array<{ start: string; end: string }>;
  dateOverrides: Record<string, TimeWindow | null>;
}

// ThreadContext (stored in D1)
export interface ThreadContext {
  id: string;
  personaId: string;
  clientPhone: string;
  state: ThreadState;
  revision: number;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
  previousState: string | null;
  metadata: Record<string, unknown>;
}

// ClientMessage
export interface ClientMessage {
  phoneNumber: string;
  body: string;
  timestamp: string;
  threadId: string;
}

// AgentMessage
export interface AgentMessage {
  body: string;
  timestamp: string;
  threadId: string;
  worker: string;
  confidence: number;
}

// DepositRecord
export interface DepositRecord {
  id: string;
  threadId: string;
  amount: number;
  currency: string;
  status: DepositStatus;
  evidenceType: string | null;
  evidenceRef: string | null;
  createdAt: string;
  verifiedAt: string | null;
}

// Message record (stored in D1)
export interface MessageRecord {
  id: string;
  threadId: string;
  direction: "inbound" | "outbound";
  body: string;
  timestamp: string;
  worker: string | null;
  confidence: number | null;
}

// AuditLog entry
export interface AuditLog {
  id: string;
  threadId: string | null;
  action: string;
  actor: string;
  details: Record<string, unknown> | null;
  timestamp: string;
}

// InferenceRequest
export interface InferenceRequest {
  model: string;
  messages: Array<{ role: "system" | "user" | "assistant"; content: string }>;
  temperature?: number;
  maxTokens?: number;
  responseFormat?: {
    type: "json_schema";
    json_schema: {
      name: string;
      strict: boolean;
      schema: Record<string, unknown>;
    };
  } | null;
}

// InferenceResponse
export interface InferenceResponse {
  content: string;
  structuredContent: Record<string, unknown> | null;
  confidence: number;
  tokensUsed: number;
  model: string;
  latencyMs?: number;
}

// SafetyCheck result
export interface SafetyCheckResult {
  verdict: SafetyVerdictType;
  reasonCode: EscalationReasonCode | null;
  confidence: number;
  direction: "inbound" | "outbound";
}

// TimingOutput
export interface TimingOutput {
  delayMs: number;
  batchGapMs: number;
}

// Worker Env bindings
export interface Env {
  DB: D1Database;
  LLM_QUEUE: Queue<LLMQueueMessage>;
  SMS_QUEUE: Queue<SMSQueueMessage>;
  LLM_DLQ: Queue<LLMQueueMessage>;
  SMS_DLQ: Queue<SMSQueueMessage>;
  OPENROUTER_API_KEY: string;
  OPENROUTER_MODEL: string;
  OPENROUTER_FALLBACK_MODEL: string;
  OPENROUTER_URL: string;
  STALL_THRESHOLD_MINUTES: string;
  CLEANUP_TTL_HOURS: string;
  RATE_LIMIT_PER_MINUTE: string;
  DAILY_RATE_LIMIT: string;
  DEFAULT_DEPOSIT_CURRENCY: string;
  SMS_BLACKLIST?: string;
}

// Queue message types
export interface LLMQueueMessage {
  threadId: string;
  messageId: string;
  clientMessageId: string;
  clientPhone: string;
  body: string;
  personaId: string;
}

export interface SMSQueueMessage {
  threadId: string;
  messageId: string;
  body: string;
  phoneNumber: string;
  deviceId: string;
  delaySeconds: number;
  sequence: number;
}

// API response envelope
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: { code: string; message: string };
  pagination?: {
    page: number;
    pageSize: number;
    total: number;
    hasMore: boolean;
  };
}
