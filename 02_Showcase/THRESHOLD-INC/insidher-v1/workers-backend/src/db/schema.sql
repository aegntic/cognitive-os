-- Insidher Backend D1 Schema
-- 7 tables with FK constraints, CHECK constraints, indexes, defaults, and cascade rules

PRAGMA foreign_keys = ON;

-- Thread states (12 variants matching Kotlin ThreadState sealed hierarchy)
-- NEW, GREETING, CONVERSING, DEPOSIT_REQUESTED, DEPOSIT_PENDING,
-- HUMAN_REVIEW, CONFIRMED, ESCALATED, ENDED, STALLED, AI_CHALLENGED, COOLDOWN

CREATE TABLE IF NOT EXISTS threads (
    id TEXT PRIMARY KEY,
    persona_id TEXT NOT NULL,
    client_phone TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'NEW' CHECK (
        state IN ('NEW', 'GREETING', 'CONVERSING', 'DEPOSIT_REQUESTED',
                  'DEPOSIT_PENDING', 'HUMAN_REVIEW', 'CONFIRMED',
                  'ESCALATED', 'ENDED', 'STALLED', 'AI_CHALLENGED', 'COOLDOWN')
    ),
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_message_at TEXT,
    previous_state TEXT,
    metadata TEXT DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    thread_id TEXT NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('inbound', 'outbound')),
    body TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    worker TEXT,
    confidence REAL,
    FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS personas (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    tone TEXT NOT NULL,
    vocabulary TEXT,
    offerings TEXT,
    deposit_wording TEXT,
    boundaries TEXT,
    availability_policy TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS deposits (
    id TEXT PRIMARY KEY,
    thread_id TEXT NOT NULL,
    amount REAL NOT NULL,
    currency TEXT NOT NULL DEFAULT 'AUD',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'RECEIVED', 'VERIFIED', 'FAILED')
    ),
    evidence_type TEXT CHECK (
        evidence_type IS NULL OR evidence_type IN ('STRIPE_WEBHOOK', 'MANUAL_FLAG')
    ),
    evidence_ref TEXT,
    created_at TEXT NOT NULL,
    verified_at TEXT,
    FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    thread_id TEXT,
    action TEXT NOT NULL,
    actor TEXT NOT NULL,
    details TEXT,
    timestamp TEXT NOT NULL
    -- No FK on thread_id for audit_logs: audit records survive thread deletion
);

CREATE TABLE IF NOT EXISTS device_keys (
    id TEXT PRIMARY KEY,
    public_key TEXT NOT NULL,
    device_name TEXT,
    registered_at TEXT NOT NULL,
    last_seen_at TEXT,
    revoked INTEGER NOT NULL DEFAULT 0,
    -- For replay protection: store seen nonces
    seen_nonces TEXT DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS thread_memory (
    id TEXT PRIMARY KEY,
    thread_id TEXT NOT NULL,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    confidence REAL DEFAULT 1.0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE CASCADE
);

-- For outbound SMS polling (Android PollWorker)
CREATE TABLE IF NOT EXISTS outbound_sms (
    id TEXT PRIMARY KEY,
    thread_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    body TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    scheduled_for TEXT NOT NULL,
    enqueued_at TEXT NOT NULL,
    delivered INTEGER NOT NULL DEFAULT 0,
    delivered_at TEXT,
    sequence INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (thread_id) REFERENCES threads(id) ON DELETE CASCADE
);

-- For rate limiting tracking
CREATE TABLE IF NOT EXISTS rate_limits (
    id TEXT PRIMARY KEY,
    identifier TEXT NOT NULL,
    window_start TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    UNIQUE(identifier)
);

-- For auth failure tracking (brute force protection)
CREATE TABLE IF NOT EXISTS auth_failures (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    failed_at TEXT NOT NULL
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_messages_thread ON messages(thread_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_threads_state ON threads(state, updated_at);
CREATE INDEX IF NOT EXISTS idx_audit_thread ON audit_logs(thread_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_memory_thread ON thread_memory(thread_id);
CREATE INDEX IF NOT EXISTS idx_outbound_sms_device ON outbound_sms(device_id, delivered, scheduled_for);
CREATE INDEX IF NOT EXISTS idx_threads_phone ON threads(client_phone, state);
CREATE INDEX IF NOT EXISTS idx_deposits_thread ON deposits(thread_id, created_at);
CREATE INDEX IF NOT EXISTS idx_auth_failures_device ON auth_failures(device_id, failed_at);
