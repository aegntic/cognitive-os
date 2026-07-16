import type { DepositRecord, DepositStatus, EvidenceType } from "../types";
import { DEPOSIT_TRANSITIONS, VALID_DEPOSIT_STATUSES } from "../types";
import { Errors } from "../errors";

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

export async function updateDepositStatus(
  db: D1Database,
  threadId: string,
  depositId: string,
  newStatus: DepositStatus,
  evidence?: { type: EvidenceType; ref: string },
): Promise<DepositRecord> {
  const deposit = await getDeposit(db, threadId, depositId);

  if (!VALID_DEPOSIT_STATUSES.includes(newStatus)) {
    throw Errors.badRequest("Invalid deposit status");
  }

  // Check valid transition
  const allowed = DEPOSIT_TRANSITIONS[deposit.status] ?? [];
  if (!allowed.includes(newStatus)) {
    throw Errors.conflict(
      `Invalid deposit transition: ${deposit.status} → ${newStatus}`,
    );
  }

  // Evidence required for VERIFIED
  if (newStatus === "VERIFIED") {
    if (!evidence || !evidence.type || !evidence.ref) {
      throw Errors.badRequest("Evidence required for VERIFIED status");
    }
  }

  const now = new Date().toISOString();
  const verifiedAt = newStatus === "VERIFIED" ? now : null;

  const details = JSON.stringify({
    from: deposit.status,
    to: newStatus,
    evidenceType: evidence?.type ?? null,
  });

  await db.batch([
    db
      .prepare(
        `UPDATE deposits
         SET status = ?, evidence_type = ?, evidence_ref = ?, verified_at = ?
         WHERE id = ?`,
      )
      .bind(
        newStatus,
        evidence?.type ?? deposit.evidenceType,
        evidence?.ref ?? deposit.evidenceRef,
        verifiedAt ?? deposit.verifiedAt,
        depositId,
      ),
    db
      .prepare(
        `INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp)
         VALUES (?, ?, 'deposit_status_change', 'system', ?, ?)`,
      )
      .bind(crypto.randomUUID(), threadId, details, now),
  ]);

  return getDeposit(db, threadId, depositId);
}
