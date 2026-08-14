import type { DepositRecord, DepositStatus, EvidenceType } from "../types";
import { DEPOSIT_TRANSITIONS, VALID_DEPOSIT_STATUSES } from "../types";
import { Errors } from "../errors";
import { logDepositAction, logSafetyCheck, writeAuditLog } from "./audit";
import { getThreadOrNull, transitionState } from "./threads";

function rowToDeposit(row: Record<string, unknown>): DepositRecord {
  return {
    id: row.id as string,
    threadId: row.thread_id as string,
    amount: row.amount as number,
    currency: row.currency as string,
    status: row.status as DepositStatus,
    evidenceType: (row.evidence_type as string) ?? null,
    evidenceRef: (row.evidence_ref as string) ?? null,
    createdAt: row.created_at as string,
    verifiedAt: (row.verified_at as string) ?? null,
  };
}

export async function createDeposit(
  db: D1Database,
  data: {
    id: string;
    threadId: string;
    amount: number;
    currency?: string;
    actor?: string;
  },
): Promise<DepositRecord> {
  const now = new Date().toISOString();
  const currency = data.currency ?? "AUD";

  await db
    .prepare(
      `INSERT INTO deposits (id, thread_id, amount, currency, status, created_at)
       VALUES (?, ?, ?, ?, 'PENDING', ?)`,
    )
    .bind(data.id, data.threadId, data.amount, currency, now)
    .run();

  await logDepositAction(db, {
    threadId: data.threadId,
    depositId: data.id,
    action: "create",
    actor: data.actor ?? "system",
    details: { amount: data.amount, currency },
  });

  return getDeposit(db, data.threadId, data.id);
}

export async function getDeposit(
  db: D1Database,
  _threadId: string,
  depositId: string,
): Promise<DepositRecord> {
  const row = await db
    .prepare("SELECT * FROM deposits WHERE id = ?")
    .bind(depositId)
    .first();
  if (!row) throw Errors.notFound("Deposit not found");
  return rowToDeposit(row);
}

export async function listDeposits(
  db: D1Database,
  threadId: string,
): Promise<DepositRecord[]> {
  const result = await db
    .prepare(
      "SELECT * FROM deposits WHERE thread_id = ? ORDER BY created_at DESC",
    )
    .bind(threadId)
    .all();
  const rows = (result.results ?? []) as unknown as Record<string, unknown>[];
  return rows.map(rowToDeposit);
}

/** Attach evidence without verifying (RECEIVED or PENDING → evidence stored). */
export async function attachEvidence(
  db: D1Database,
  threadId: string,
  depositId: string,
  evidence: { type: EvidenceType; ref: string },
  actor = "system",
): Promise<DepositRecord> {
  const deposit = await getDeposit(db, threadId, depositId);

  if (deposit.status === "VERIFIED" || deposit.status === "FAILED") {
    throw Errors.conflict(
      `Cannot attach evidence to deposit in ${deposit.status} status`,
    );
  }

  if (!evidence.type || !evidence.ref) {
    throw Errors.badRequest("evidenceType and evidenceRef required");
  }

  if (evidence.type !== "STRIPE_WEBHOOK" && evidence.type !== "MANUAL_FLAG") {
    throw Errors.badRequest("Invalid evidenceType (STRIPE_WEBHOOK | MANUAL_FLAG)");
  }

  // If still PENDING, mark RECEIVED when evidence arrives
  const nextStatus: DepositStatus =
    deposit.status === "PENDING" ? "RECEIVED" : deposit.status;

  const now = new Date().toISOString();
  await db.batch([
    db
      .prepare(
        `UPDATE deposits
         SET status = ?, evidence_type = ?, evidence_ref = ?
         WHERE id = ?`,
      )
      .bind(nextStatus, evidence.type, evidence.ref, depositId),
    db
      .prepare(
        `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
         VALUES (?, ?, 'deposit', ?, ?, ?)`,
      )
      .bind(
        crypto.randomUUID(),
        threadId,
        actor,
        JSON.stringify({
          depositId,
          depositAction: "evidence_upload",
          evidenceType: evidence.type,
          evidenceRef: evidence.ref,
          from: deposit.status,
          to: nextStatus,
        }),
        now,
      ),
  ]);

  return getDeposit(db, threadId, depositId);
}

export async function updateDepositStatus(
  db: D1Database,
  threadId: string,
  depositId: string,
  newStatus: DepositStatus,
  evidence?: { type: EvidenceType; ref: string },
  options?: {
    actor?: string;
    reason?: string;
    advanceThread?: boolean;
  },
): Promise<DepositRecord> {
  const deposit = await getDeposit(db, threadId, depositId);
  const actor = options?.actor ?? "system";

  if (!VALID_DEPOSIT_STATUSES.includes(newStatus)) {
    throw Errors.badRequest("Invalid deposit status");
  }

  const allowed = DEPOSIT_TRANSITIONS[deposit.status] ?? [];
  if (!allowed.includes(newStatus)) {
    throw Errors.conflict(
      `Invalid deposit transition: ${deposit.status} → ${newStatus}`,
    );
  }

  // Evidence required for VERIFIED — use provided or already attached
  let evidenceType = evidence?.type ?? (deposit.evidenceType as EvidenceType | null);
  let evidenceRef = evidence?.ref ?? deposit.evidenceRef;

  if (newStatus === "VERIFIED") {
    if (!evidenceType || !evidenceRef) {
      throw Errors.badRequest("Evidence required for VERIFIED status");
    }
    if (evidenceType !== "STRIPE_WEBHOOK" && evidenceType !== "MANUAL_FLAG") {
      throw Errors.badRequest("Invalid evidenceType (STRIPE_WEBHOOK | MANUAL_FLAG)");
    }
  }

  const now = new Date().toISOString();
  const verifiedAt = newStatus === "VERIFIED" ? now : deposit.verifiedAt;

  const details = {
    depositId,
    depositAction: "status_change",
    from: deposit.status,
    to: newStatus,
    evidenceType: evidenceType ?? null,
    reason: options?.reason ?? null,
  };

  await db.batch([
    db
      .prepare(
        `UPDATE deposits
         SET status = ?, evidence_type = ?, evidence_ref = ?, verified_at = ?
         WHERE id = ?`,
      )
      .bind(
        newStatus,
        evidenceType ?? deposit.evidenceType,
        evidenceRef ?? deposit.evidenceRef,
        verifiedAt,
        depositId,
      ),
    db
      .prepare(
        `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
         VALUES (?, ?, 'deposit', ?, ?, ?)`,
      )
      .bind(crypto.randomUUID(), threadId, actor, JSON.stringify(details), now),
    db
      .prepare(
        `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
         VALUES (?, ?, 'deposit_status_change', ?, ?, ?)`,
      )
      .bind(
        crypto.randomUUID(),
        threadId,
        actor,
        JSON.stringify({ from: deposit.status, to: newStatus, evidenceType }),
        now,
      ),
  ]);

  // Safety hook on verify
  if (newStatus === "VERIFIED") {
    await logSafetyCheck(db, {
      threadId,
      verdict: "SAFE",
      direction: "inbound",
      confidence: 1.0,
      context: "deposit_verify",
      contentSnippet: `deposit:${depositId}:${evidenceRef}`,
    });

    // Advance DEPOSIT_PENDING → HUMAN_REVIEW when deposit verified
    if (options?.advanceThread !== false) {
      const thread = await getThreadOrNull(db, threadId);
      if (thread?.state === "DEPOSIT_PENDING") {
        await transitionState(
          db,
          threadId,
          "HUMAN_REVIEW",
          thread.revision,
          actor,
          { reason: "deposit_verified", depositId },
        );
      }
    }
  }

  return getDeposit(db, threadId, depositId);
}

/** Evidence timeout / explicit fail (PENDING|RECEIVED → FAILED). */
export async function failDeposit(
  db: D1Database,
  threadId: string,
  depositId: string,
  reason: "evidence_timeout" | "client_decline" | "failed" | string,
  actor = "system",
): Promise<DepositRecord> {
  return updateDepositStatus(db, threadId, depositId, "FAILED", undefined, {
    actor,
    reason,
    advanceThread: false,
  });
}

/**
 * Retry after FAILED: create a new PENDING deposit (FAILED is terminal per contracts).
 * Optionally returns thread to DEPOSIT_REQUESTED for a fresh ask.
 */
export async function retryDeposit(
  db: D1Database,
  threadId: string,
  failedDepositId: string,
  options?: { amount?: number; currency?: string; actor?: string },
): Promise<DepositRecord> {
  const failed = await getDeposit(db, threadId, failedDepositId);
  if (failed.status !== "FAILED") {
    throw Errors.conflict("Only FAILED deposits can be retried");
  }

  const actor = options?.actor ?? "system";
  const newId = crypto.randomUUID();
  const deposit = await createDeposit(db, {
    id: newId,
    threadId,
    amount: options?.amount ?? failed.amount,
    currency: options?.currency ?? failed.currency,
    actor,
  });

  await logDepositAction(db, {
    threadId,
    depositId: newId,
    action: "retry",
    actor,
    details: { previousDepositId: failedDepositId },
  });

  const thread = await getThreadOrNull(db, threadId);
  if (
    thread &&
    (thread.state === "DEPOSIT_PENDING" || thread.state === "ENDED")
  ) {
    // Re-open to DEPOSIT_REQUESTED when allowed
    if (thread.state === "DEPOSIT_PENDING") {
      await transitionState(
        db,
        threadId,
        "DEPOSIT_REQUESTED",
        thread.revision,
        actor,
        { reason: "deposit_retry", previousDepositId: failedDepositId },
      );
    }
  }

  return deposit;
}

/** Expire stale PENDING deposits (cron). */
export async function expireStaleDeposits(
  db: D1Database,
  thresholdIso: string,
): Promise<number> {
  const result = await db
    .prepare(
      `SELECT id, thread_id FROM deposits
       WHERE status IN ('PENDING', 'RECEIVED') AND created_at < ?`,
    )
    .bind(thresholdIso)
    .all();

  const rows = (result.results ?? []) as unknown as Array<{
    id: string;
    thread_id: string;
  }>;

  let count = 0;
  for (const row of rows) {
    try {
      await failDeposit(
        db,
        row.thread_id,
        row.id,
        "evidence_timeout",
        "system:cleanup",
      );
      count++;
    } catch {
      // race / already transitioned
    }
  }

  if (count > 0) {
    await writeAuditLog(db, {
      threadId: null,
      action: "cleanup_expired_deposits",
      actor: "system",
      details: { expiredCount: count, threshold: thresholdIso },
    });
  }

  return count;
}
