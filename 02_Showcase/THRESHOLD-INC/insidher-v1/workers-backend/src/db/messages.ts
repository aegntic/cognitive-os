import type { MessageRecord } from "../types";
import { Errors } from "../errors";

function rowToMessage(row: Record<string, unknown>): MessageRecord {
  return {
    id: row.id as string,
    threadId: row.thread_id as string,
    direction: row.direction as "inbound" | "outbound",
    body: row.body as string,
    timestamp: row.timestamp as string,
    worker: (row.worker as string) ?? null,
    confidence: row.confidence !== null && row.confidence !== undefined
      ? (row.confidence as number)
      : null,
  };
}

export async function createMessage(
  db: D1Database,
  data: {
    id: string;
    threadId: string;
    direction: "inbound" | "outbound";
    body: string;
    timestamp: string;
    worker?: string | null;
    confidence?: number | null;
  },
): Promise<MessageRecord> {
  await db
    .prepare(
      `INSERT INTO messages (id, thread_id, direction, body, timestamp, worker, confidence)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      data.id,
      data.threadId,
      data.direction,
      data.body,
      data.timestamp,
      data.worker ?? null,
      data.confidence ?? null,
    )
    .run();

  return getMessage(db, data.id);
}

export async function getMessage(
  db: D1Database,
  messageId: string,
): Promise<MessageRecord> {
  const row = await db
    .prepare("SELECT * FROM messages WHERE id = ?")
    .bind(messageId)
    .first();
  if (!row) throw Errors.notFound("Message not found");
  return rowToMessage(row);
}

export async function getMessageOrNull(
  db: D1Database,
  messageId: string,
): Promise<MessageRecord | null> {
  const row = await db
    .prepare("SELECT * FROM messages WHERE id = ?")
    .bind(messageId)
    .first();
  if (!row) return null;
  return rowToMessage(row);
}

export async function listMessages(
  db: D1Database,
  threadId: string,
  options: { page?: number; pageSize?: number } = {},
): Promise<{ messages: MessageRecord[]; total: number }> {
  const page = options.page ?? 1;
  const pageSize = options.pageSize ?? 50;
  const offset = (page - 1) * pageSize;

  const [rowsResult, countResult] = await db.batch([
    db
      .prepare(
        "SELECT * FROM messages WHERE thread_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?",
      )
      .bind(threadId, pageSize, offset),
    db
      .prepare("SELECT COUNT(*) as total FROM messages WHERE thread_id = ?")
      .bind(threadId),
  ]);

  const rows = (rowsResult.results ?? []) as unknown as Record<string, unknown>[];
  const total =
    ((countResult.results?.[0] as Record<string, unknown>)?.total as number) ?? 0;

  return {
    messages: rows.map(rowToMessage),
    total,
  };
}
