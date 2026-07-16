import { Errors } from "../errors";

export interface OutboundSms {
  id: string;
  threadId: string;
  messageId: string;
  deviceId: string;
  body: string;
  phoneNumber: string;
  scheduledFor: string;
  enqueuedAt: string;
  delivered: number;
  deliveredAt: string | null;
  sequence: number;
}

function rowToOutboundSms(row: Record<string, unknown>): OutboundSms {
  return {
    id: row.id as string,
    threadId: row.thread_id as string,
    messageId: row.message_id as string,
    deviceId: row.device_id as string,
    body: row.body as string,
    phoneNumber: row.phone_number as string,
    scheduledFor: row.scheduled_for as string,
    enqueuedAt: row.enqueued_at as string,
    delivered: row.delivered as number,
    deliveredAt: (row.delivered_at as string) ?? null,
    sequence: row.sequence as number,
  };
}

export async function enqueueOutboundSms(
  db: D1Database,
  data: {
    id: string;
    threadId: string;
    messageId: string;
    deviceId: string;
    body: string;
    phoneNumber: string;
    scheduledFor: string;
    sequence: number;
  },
): Promise<OutboundSms> {
  const now = new Date().toISOString();

  await db
    .prepare(
      `INSERT INTO outbound_sms (id, thread_id, message_id, device_id, body, phone_number, scheduled_for, enqueued_at, delivered, sequence)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`,
    )
    .bind(
      data.id,
      data.threadId,
      data.messageId,
      data.deviceId,
      data.body,
      data.phoneNumber,
      data.scheduledFor,
      now,
      data.sequence,
    )
    .run();

  const row = await db
    .prepare("SELECT * FROM outbound_sms WHERE id = ?")
    .bind(data.id)
    .first();
  if (!row) throw Errors.internal("Failed to enqueue SMS");
  return rowToOutboundSms(row);
}

export async function getPendingOutboundSms(
  db: D1Database,
  deviceId: string,
): Promise<OutboundSms[]> {
  const now = new Date().toISOString();
  const result = await db
    .prepare(
      `SELECT * FROM outbound_sms
       WHERE device_id = ? AND delivered = 0 AND scheduled_for <= ?
       ORDER BY sequence ASC, enqueued_at ASC`,
    )
    .bind(deviceId, now)
    .all();

  const rows = (result.results ?? []) as unknown as Record<string, unknown>[];
  return rows.map(rowToOutboundSms);
}

export async function markOutboundSmsDelivered(
  db: D1Database,
  smsId: string,
): Promise<void> {
  const now = new Date().toISOString();
  await db
    .prepare("UPDATE outbound_sms SET delivered = 1, delivered_at = ? WHERE id = ?")
    .bind(now, smsId)
    .run();
}

export async function getNextSequence(db: D1Database, threadId: string): Promise<number> {
  const row = await db
    .prepare(
      "SELECT COALESCE(MAX(sequence), 0) as max_seq FROM outbound_sms WHERE thread_id = ?",
    )
    .bind(threadId)
    .first();
  return ((row?.max_seq as number) ?? 0) + 1;
}
