import { Hono } from "hono";
import type { Env, ApiResponse, LLMQueueMessage } from "../types";
import { errorResponse } from "../errors";
import { createThread,
  findActiveThreadByPhone,
  updateLastMessageAt,
} from "../db/threads";
import { createMessage } from "../db/messages";
import { listPersonas } from "../db/personas";
import { writeAuditLog } from "../db/audit";

const sms = new Hono<{ Bindings: Env }>();

// POST /webhook/sms - Inbound SMS webhook (no device auth required)
sms.post("/", async (c) => {
  const body = await c.req.json().catch(() => null);

  // Also support form-encoded (Twilio-style)
  let smsData: Record<string, unknown>;
  if (body) {
    smsData = body;
  } else {
    const formData = await c.req.parseBody();
    smsData = formData as Record<string, unknown>;
  }

  // Extract fields (support both camelCase and Twilio-style)
  const from =
    (smsData.from as string) ??
    (smsData.From as string) ??
    (smsData.phoneNumber as string);
  const messageBody =
    (smsData.body as string) ??
    (smsData.Body as string) ??
    (smsData.message as string);
  const timestamp =
    (smsData.timestamp as string) ??
    (smsData.Timestamp as string) ??
    new Date().toISOString();

  if (!from || typeof from !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing required field: from"),
      400,
    );
  }

  if (!messageBody || typeof messageBody !== "string" || messageBody.trim() === "") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing required field: body (or Body)"),
      400,
    );
  }

  // Check for blacklisted number
  const blacklist = c.env.SMS_BLACKLIST;
  if (blacklist) {
    const blacklisted = blacklist.split(",").map((n) => n.trim());
    if (blacklisted.includes(from)) {
      await writeAuditLog(c.env.DB, {
        action: "sms_blacklisted",
        actor: "system",
        details: { phoneNumber: from },
      });
      // Return 200 to avoid Twilio retries
      const response: ApiResponse<{ status: string }> = {
        success: true,
        data: { status: "ignored" },
      };
      return c.json(response, 200);
    }
  }

  // Truncate extremely long bodies (> 1600 chars)
  const truncatedBody =
    messageBody.length > 1600 ? messageBody.substring(0, 1600) : messageBody;

  // Generate idempotency key from from + body + timestamp
  const idempotencyKey = `${from}:${truncatedBody}:${timestamp}`;

  // Check for duplicate message (idempotency)
  const existingMessage = await c.env.DB
    .prepare(
      `SELECT id FROM messages WHERE body = ? AND timestamp = ?
       AND thread_id IN (SELECT id FROM threads WHERE client_phone = ?)
       LIMIT 1`,
    )
    .bind(truncatedBody, timestamp, from)
    .first();

  if (existingMessage) {
    // Idempotent response
    const response: ApiResponse<{ status: string; idempotent: boolean }> = {
      success: true,
      data: { status: "duplicate", idempotent: true },
    };
    return c.json(response, 200);
  }

  // Find existing active thread or create new one
  let thread = await findActiveThreadByPhone(c.env.DB, from);

  if (!thread) {
    // Create new thread - use first available persona or default
    const personas = await listPersonas(c.env.DB);
    const persona = personas[0];

    if (!persona) {
      return c.json(
        errorResponse(503, "UNAVAILABLE", "No personas configured"),
        503,
      );
    }

    thread = await createThread(c.env.DB, {
      id: crypto.randomUUID(),
      personaId: persona.id,
      clientPhone: from,
      state: "NEW",
    });
  }

  // Create inbound message
  const messageId = crypto.randomUUID();
  const message = await createMessage(c.env.DB, {
    id: messageId,
    threadId: thread.id,
    direction: "inbound",
    body: truncatedBody,
    timestamp,
  });

  // Update thread's last_message_at
  await updateLastMessageAt(c.env.DB, thread.id);

  // Enqueue LLM processing
  const queueMessage: LLMQueueMessage = {
    threadId: thread.id,
    messageId,
    clientMessageId: messageId,
    clientPhone: from,
    body: truncatedBody,
    personaId: thread.personaId,
  };

  try {
    await c.env.LLM_QUEUE.send(queueMessage);
  } catch {
    await writeAuditLog(c.env.DB, {
      threadId: thread.id,
      action: "queue_enqueue_failed",
      actor: "system",
      details: { messageId, queue: "llm-queue" },
    });
  }

  // Suppress unused variable warning
  void idempotencyKey;

  const response: ApiResponse<{
    threadId: string;
    messageId: string;
    status: string;
  }> = {
    success: true,
    data: {
      threadId: thread.id,
      messageId: message.id,
      status: "accepted",
    },
  };

  return c.json(response, 202);
});

export default sms;
