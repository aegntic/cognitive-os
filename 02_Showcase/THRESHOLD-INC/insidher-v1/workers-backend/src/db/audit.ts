import type { AuditLog, SafetyVerdictType, EscalationReasonCode } from "../types";

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

/** Safety verdict hook — deposit / human-decision / outbound paths */
export async function logSafetyCheck(
  db: D1Database,
  data: {
    threadId: string;
    verdict: SafetyVerdictType;
    direction: "inbound" | "outbound";
    reasonCode?: EscalationReasonCode | string | null;
    confidence?: number;
    contentSnippet?: string | null;
    context?: string;
  },
): Promise<void> {
  await writeAuditLog(db, {
    threadId: data.threadId,
    action: "safety_check",
    actor: "worker:safety",
    details: {
      verdict: data.verdict,
      direction: data.direction,
      confidence: data.confidence ?? 1.0,
      reasonCode: data.reasonCode ?? null,
      content: data.contentSnippet ? data.contentSnippet.slice(0, 500) : null,
      context: data.context ?? null,
    },
  });
}

export async function logHumanDecision(
  db: D1Database,
  data: {
    threadId: string;
    decision: "APPROVE" | "REJECT" | "ESCALATE";
    actor?: string;
    note?: string | null;
    expectedRevision?: number | null;
  },
): Promise<void> {
  await writeAuditLog(db, {
    threadId: data.threadId,
    action: "human_decision",
    actor: data.actor ?? "owner",
    details: {
      decision: data.decision,
      note: data.note ?? null,
      expectedRevision: data.expectedRevision ?? null,
    },
  });
}

export async function logDepositAction(
  db: D1Database,
  data: {
    threadId: string;
    depositId: string;
    action: string;
    actor?: string;
    details?: Record<string, unknown>;
  },
): Promise<void> {
  await writeAuditLog(db, {
    threadId: data.threadId,
    action: "deposit",
    actor: data.actor ?? "system",
    details: {
      depositId: data.depositId,
      depositAction: data.action,
      ...(data.details ?? {}),
    },
  });
}

/** True if an APPROVE human_decision was recorded for this thread (confirmation gate). */
export async function hasApproveDecision(
  db: D1Database,
  threadId: string,
): Promise<boolean> {
  const row = await db
    .prepare(
      `SELECT id FROM audit_logs
       WHERE thread_id = ? AND action = 'human_decision'
         AND details LIKE '%"decision":"APPROVE"%'
       LIMIT 1`,
    )
    .bind(threadId)
    .first();
  return row != null;
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
