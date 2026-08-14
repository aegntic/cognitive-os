import type { ThreadContext, ThreadState } from "../types";
import { VALID_TRANSITIONS } from "../types";
import { Errors } from "../errors";

function rowToThread(row: Record<string, unknown>): ThreadContext {
  return {
    id: row.id as string,
    personaId: row.persona_id as string,
    clientPhone: row.client_phone as string,
    state: row.state as ThreadState,
    revision: row.revision as number,
    createdAt: row.created_at as string,
    updatedAt: row.updated_at as string,
    lastMessageAt: (row.last_message_at as string) ?? null,
    previousState: (row.previous_state as string) ?? null,
    metadata: row.metadata ? JSON.parse(row.metadata as string) : {},
  };
}

export async function createThread(
  db: D1Database,
  data: {
    id: string;
    personaId: string;
    clientPhone: string;
    state?: ThreadState;
    metadata?: Record<string, unknown>;
  },
): Promise<ThreadContext> {
  const now = new Date().toISOString();
  const state = data.state ?? "NEW";
  const metadata = JSON.stringify(data.metadata ?? {});

  await db
    .prepare(
      `INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at, metadata)
       VALUES (?, ?, ?, ?, 1, ?, ?, ?)`,
    )
    .bind(data.id, data.personaId, data.clientPhone, state, now, now, metadata)
    .run();

  return getThread(db, data.id);
}

export async function getThread(db: D1Database, threadId: string): Promise<ThreadContext> {
  const row = await db.prepare("SELECT * FROM threads WHERE id = ?").bind(threadId).first();
  if (!row) throw Errors.notFound("Thread not found");
  return rowToThread(row);
}

export async function getThreadOrNull(
  db: D1Database,
  threadId: string,
): Promise<ThreadContext | null> {
  const row = await db.prepare("SELECT * FROM threads WHERE id = ?").bind(threadId).first();
  if (!row) return null;
  return rowToThread(row);
}

export async function findActiveThreadByPhone(
  db: D1Database,
  phone: string,
): Promise<ThreadContext | null> {
  const row = await db
    .prepare(
      `SELECT * FROM threads
       WHERE client_phone = ? AND state NOT IN ('ENDED', 'CONFIRMED', 'ESCALATED')
       ORDER BY updated_at DESC LIMIT 1`,
    )
    .bind(phone)
    .first();
  if (!row) return null;
  return rowToThread(row);
}

// Atomic state transition using D1 batch() with CAS on revision
export async function transitionState(
  db: D1Database,
  threadId: string,
  newState: ThreadState,
  expectedRevision: number,
  actor = "system",
  extraDetails?: Record<string, unknown>,
): Promise<{ success: boolean; thread: ThreadContext | null }> {
  // First check if transition is valid
  const thread = await getThreadOrNull(db, threadId);
  if (!thread) {
    throw Errors.notFound("Thread not found");
  }

  const allowed = VALID_TRANSITIONS[thread.state] ?? [];
  if (!allowed.includes(newState)) {
    throw Errors.conflict(
      `Invalid transition: ${thread.state} → ${newState}`,
    );
  }

  const now = new Date().toISOString();
  const details = JSON.stringify({
    from: thread.state,
    to: newState,
    ...(extraDetails ?? {}),
  });

  // Atomic batch: UPDATE with CAS + INSERT audit log
  const result = await db.batch([
    db
      .prepare(
        `UPDATE threads
         SET state = ?, revision = revision + 1, updated_at = ?, previous_state = ?
         WHERE id = ? AND revision = ?`,
      )
      .bind(newState, now, thread.state, threadId, expectedRevision),
    db
      .prepare(
        `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
         VALUES (?, ?, 'state_transition', ?, ?, ?)`,
      )
      .bind(crypto.randomUUID(), threadId, actor, details, now),
  ]);

  // Check if the UPDATE affected any rows (CAS check)
  const updateMeta = result[0]?.meta;
  if (!updateMeta || (updateMeta.changes ?? 0) === 0) {
    return { success: false, thread: null };
  }

  const updated = await getThreadOrNull(db, threadId);
  return { success: true, thread: updated };
}

// List threads with pagination and optional state filter
export async function listThreads(
  db: D1Database,
  options: {
    page?: number;
    pageSize?: number;
    state?: ThreadState;
  } = {},
): Promise<{ threads: ThreadContext[]; total: number }> {
  const page = options.page ?? 1;
  const pageSize = options.pageSize ?? 20;
  const offset = (page - 1) * pageSize;

  let query = "SELECT * FROM threads";
  let countQuery = "SELECT COUNT(*) as total FROM threads";
  const params: (string | number)[] = [];

  if (options.state) {
    query += " WHERE state = ?";
    countQuery += " WHERE state = ?";
    params.push(options.state);
  }

  query += " ORDER BY updated_at DESC LIMIT ? OFFSET ?";
  const queryParams = [...params, pageSize, offset];

  const [rowsResult, countResult] = await db.batch([
    db.prepare(query).bind(...queryParams),
    db.prepare(countQuery).bind(...params),
  ]);

  const rows = (rowsResult.results ?? []) as unknown as Record<string, unknown>[];
  const total = ((countResult.results?.[0] as Record<string, unknown>)?.total as number) ?? 0;

  return {
    threads: rows.map(rowToThread),
    total,
  };
}

// Update last_message_at
export async function updateLastMessageAt(
  db: D1Database,
  threadId: string,
): Promise<void> {
  const now = new Date().toISOString();
  await db
    .prepare("UPDATE threads SET last_message_at = ?, updated_at = ? WHERE id = ?")
    .bind(now, now, threadId)
    .run();
}

// End thread
export async function endThread(
  db: D1Database,
  threadId: string,
): Promise<ThreadContext> {
  const thread = await getThread(db, threadId);
  if (thread.state === "ENDED") {
    throw Errors.conflict("Thread is already ended");
  }

  const result = await transitionState(db, threadId, "ENDED", thread.revision);
  if (!result.success || !result.thread) {
    throw Errors.conflict("Failed to end thread (concurrent modification)");
  }
  return result.thread;
}

// Resume stalled thread
export async function resumeThread(
  db: D1Database,
  threadId: string,
): Promise<ThreadContext> {
  const thread = await getThread(db, threadId);
  if (thread.state !== "STALLED") {
    throw Errors.conflict("Thread is not stalled");
  }

  const result = await transitionState(db, threadId, "CONVERSING", thread.revision);
  if (!result.success || !result.thread) {
    throw Errors.conflict("Failed to resume thread (concurrent modification)");
  }
  return result.thread;
}
