import type { AuditLog } from "../types";

export async function writeAuditLog(
  db: D1Database,
  data: {
    threadId?: string | null;
    action: string;
    actor: string;
    details?: Record<string, unknown> | null;
    timestamp?: string;
  },
): Promise<void> {
  const now = data.timestamp ?? new Date().toISOString();
  await db
    .prepare(
      `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
       VALUES (?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      crypto.randomUUID(),
      data.threadId ?? null,
      data.action,
      data.actor,
      data.details ? JSON.stringify(data.details) : null,
      now,
    )
    .run();
}

export async function getAuditLogs(
  db: D1Database,
  threadId: string,
): Promise<AuditLog[]> {
  const result = await db
    .prepare(
      "SELECT * FROM audit_logs WHERE thread_id = ? ORDER BY timestamp DESC",
    )
    .bind(threadId)
    .all();

  const rows = (result.results ?? []) as unknown as Record<string, unknown>[];
  return rows.map((row) => ({
    id: row.id as string,
    threadId: (row.thread_id as string) ?? null,
    action: row.action as string,
    actor: row.actor as string,
    details: row.details ? JSON.parse(row.details as string) : null,
    timestamp: row.timestamp as string,
  }));
}
