import { Hono } from "hono";
import type { Env, ApiResponse, ThreadState, LLMQueueMessage } from "../types";
import { ApiError, errorResponse, asStatusCode } from "../errors";
import {
  createThread,
  getThreadOrNull,
  listThreads,
  transitionState,
  endThread,
  resumeThread,
  updateLastMessageAt,
} from "../db/threads";
import { getPersonaOrNull } from "../db/personas";
import { createMessage, listMessages } from "../db/messages";
import {
  writeAuditLog,
  logHumanDecision,
  logSafetyCheck,
  hasApproveDecision,
} from "../db/audit";

const threads = new Hono<{ Bindings: Env; Variables: { deviceId: string } }>();

// POST /api/threads - Create a new thread
threads.post("/", async (c) => {
  const body = await c.req.json().catch(() => null);
  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { personaId, clientPhone, metadata } = body;

  if (!personaId || typeof personaId !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid personaId"),
      400,
    );
  }

  if (!clientPhone || typeof clientPhone !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid clientPhone"),
      400,
    );
  }

  // Check persona exists
  const persona = await getPersonaOrNull(c.env.DB, personaId);
  if (!persona) {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Invalid persona: persona not found"),
      400,
    );
  }

  const threadId = crypto.randomUUID();
  const thread = await createThread(c.env.DB, {
    id: threadId,
    personaId,
    clientPhone,
    metadata: metadata ?? {},
  });

  const response: ApiResponse<typeof thread> = {
    success: true,
    data: thread,
  };

  return c.json(response, 201);
});

// GET /api/threads - List threads with pagination and optional state filter
threads.get("/", async (c) => {
  const page = parseInt(c.req.query("page") ?? "1", 10);
  const pageSize = parseInt(c.req.query("pageSize") ?? "20", 10);
  const state = c.req.query("state") as ThreadState | undefined;

  const { threads: threadList, total } = await listThreads(c.env.DB, {
    page,
    pageSize,
    state,
  });

  const response: ApiResponse<typeof threadList> = {
    success: true,
    data: threadList,
    pagination: {
      page,
      pageSize,
      total,
      hasMore: page * pageSize < total,
    },
  };

  return c.json(response, 200);
});

// GET /api/threads/:threadId - Get a single thread
threads.get("/:threadId", async (c) => {
  const threadId = c.req.param("threadId")!;
  const thread = await getThreadOrNull(c.env.DB, threadId);

  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const response: ApiResponse<typeof thread> = {
    success: true,
    data: thread,
  };

  return c.json(response, 200);
});

// GET /api/threads/:threadId/state - Get thread state
threads.get("/:threadId/state", async (c) => {
  const threadId = c.req.param("threadId")!;
  const thread = await getThreadOrNull(c.env.DB, threadId);

  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const response: ApiResponse<{ state: ThreadState; revision: number }> = {
    success: true,
    data: { state: thread.state, revision: thread.revision },
  };

  return c.json(response, 200);
});

// POST /api/threads/:threadId/messages - Submit a message to a thread
threads.post("/:threadId/messages", async (c) => {
  const threadId = c.req.param("threadId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { body: messageBody, direction } = body;

  if (!messageBody || typeof messageBody !== "string" || messageBody.trim() === "") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or empty body"),
      400,
    );
  }

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  // Check thread state - don't accept messages for ended threads
  if (thread.state === "ENDED" || thread.state === "CONFIRMED") {
    return c.json(
      errorResponse(409, "CONFLICT", `Cannot submit message to thread in ${thread.state} state`),
      409,
    );
  }

  // Create message
  const messageDirection = direction ?? "inbound";
  const messageId = crypto.randomUUID();
  const timestamp = new Date().toISOString();

  const message = await createMessage(c.env.DB, {
    id: messageId,
    threadId,
    direction: messageDirection,
    body: messageBody,
    timestamp,
  });

  // Update thread's last_message_at
  await updateLastMessageAt(c.env.DB, threadId);

  // Enqueue LLM processing
  const queueMessage: LLMQueueMessage = {
    threadId,
    messageId,
    clientMessageId: messageId,
    clientPhone: thread.clientPhone,
    body: messageBody,
    personaId: thread.personaId,
  };

  try {
    await c.env.LLM_QUEUE.send(queueMessage);
  } catch {
    // Queue failure - message is persisted but processing will be retried
    await writeAuditLog(c.env.DB, {
      threadId,
      action: "queue_enqueue_failed",
      actor: "system",
      details: { messageId, queue: "llm-queue" },
    });
    return c.json(
      errorResponse(503, "UNAVAILABLE", "Queue temporarily unavailable. Message saved but processing delayed."),
      503,
    );
  }

  const response: ApiResponse<typeof message> = {
    success: true,
    data: message,
  };

  return c.json(response, 202);
});

// GET /api/threads/:threadId/messages - List messages with pagination
threads.get("/:threadId/messages", async (c) => {
  const threadId = c.req.param("threadId")!;
  const page = parseInt(c.req.query("page") ?? "1", 10);
  const pageSize = parseInt(c.req.query("pageSize") ?? "50", 10);

  // Verify thread exists
  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const { messages: messageList, total } = await listMessages(c.env.DB, threadId, {
    page,
    pageSize,
  });

  const response: ApiResponse<typeof messageList> = {
    success: true,
    data: messageList,
    pagination: {
      page,
      pageSize,
      total,
      hasMore: page * pageSize < total,
    },
  };

  return c.json(response, 200);
});

// POST /api/threads/:threadId/transition - Transition thread state (with CAS)
threads.post("/:threadId/transition", async (c) => {
  const threadId = c.req.param("threadId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { newState, expectedRevision, actor } = body;

  if (!newState || typeof newState !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid newState"),
      400,
    );
  }

  if (typeof expectedRevision !== "number") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid expectedRevision"),
      400,
    );
  }

  try {
    const result = await transitionState(
      c.env.DB,
      threadId,
      newState as ThreadState,
      expectedRevision,
      actor ?? "system",
    );

    if (!result.success || !result.thread) {
      return c.json(
        errorResponse(409, "CONFLICT", "Revision mismatch - concurrent modification detected"),
        409,
      );
    }

    const response: ApiResponse<typeof result.thread> = {
      success: true,
      data: result.thread,
    };

    return c.json(response, 200);
  } catch (err) {
    if (err instanceof ApiError) {
      return c.json(
        errorResponse(err.statusCode, err.code, err.message),
        asStatusCode(err.statusCode),
      );
    }
    throw err;
  }
});

// POST /api/threads/:threadId/end - End a thread
threads.post("/:threadId/end", async (c) => {
  const threadId = c.req.param("threadId")!;

  try {
    const thread = await endThread(c.env.DB, threadId);

    const response: ApiResponse<typeof thread> = {
      success: true,
      data: thread,
    };

    return c.json(response, 200);
  } catch (err) {
    if (err instanceof ApiError) {
      return c.json(
        errorResponse(err.statusCode, err.code, err.message),
        asStatusCode(err.statusCode),
      );
    }
    throw err;
  }
});

// POST /api/threads/:threadId/resume - Resume a stalled thread
threads.post("/:threadId/resume", async (c) => {
  const threadId = c.req.param("threadId")!;

  try {
    const thread = await resumeThread(c.env.DB, threadId);

    const response: ApiResponse<typeof thread> = {
      success: true,
      data: thread,
    };

    return c.json(response, 200);
  } catch (err) {
    if (err instanceof ApiError) {
      return c.json(
        errorResponse(err.statusCode, err.code, err.message),
        asStatusCode(err.statusCode),
      );
    }
    throw err;
  }
});

// Shared human-decision application (CAS on revision, no confirm SMS without APPROVE)
async function applyHumanDecision(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  c: any,
  threadId: string,
  decision: "APPROVE" | "REJECT" | "ESCALATE",
  opts: { note?: string | null; owner?: string; expectedRevision?: number },
): Promise<Response> {
  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  if (thread.state !== "HUMAN_REVIEW") {
    return c.json(
      errorResponse(
        409,
        "CONFLICT",
        `Thread is not in HUMAN_REVIEW state (current: ${thread.state})`,
      ),
      409,
    );
  }

  // CAS: client may supply expectedRevision; default to current (optimistic)
  const expectedRevision =
    typeof opts.expectedRevision === "number"
      ? opts.expectedRevision
      : thread.revision;

  if (expectedRevision !== thread.revision) {
    return c.json(
      errorResponse(
        409,
        "CONFLICT",
        "Revision mismatch - concurrent modification detected",
      ),
      409,
    );
  }

  let targetState: ThreadState;
  if (decision === "APPROVE") {
    targetState = "CONFIRMED";
  } else if (decision === "REJECT") {
    targetState = "ENDED";
  } else {
    targetState = "ESCALATED";
  }

  const actor = opts.owner ?? (c.get("deviceId") as string) ?? "owner";

  const result = await transitionState(
    c.env.DB,
    threadId,
    targetState,
    expectedRevision,
    actor,
    { humanDecision: decision },
  );

  if (!result.success || !result.thread) {
    return c.json(
      errorResponse(
        409,
        "CONFLICT",
        "Failed to apply decision (concurrent modification)",
      ),
      409,
    );
  }

  await logHumanDecision(c.env.DB, {
    threadId,
    decision,
    actor,
    note: opts.note ?? null,
    expectedRevision,
  });

  // Safety hook on human decision path
  await logSafetyCheck(c.env.DB, {
    threadId,
    verdict: "SAFE",
    direction: "outbound",
    context: `human_decision:${decision}`,
    contentSnippet: opts.note ?? decision,
  });

  // Invariant: confirmation SMS only after HumanDecision.APPROVE
  if (decision === "APPROVE") {
    // Double-check gate (audit row must exist before enqueue)
    const approved = await hasApproveDecision(c.env.DB, threadId);
    if (!approved) {
      await writeAuditLog(c.env.DB, {
        threadId,
        action: "block_final_sms",
        actor: "system",
        details: { reason: "missing_approve_audit" },
      });
    } else {
      const confirmBody = "Your booking is confirmed! See you soon. 💋";
      await logSafetyCheck(c.env.DB, {
        threadId,
        verdict: "SAFE",
        direction: "outbound",
        context: "confirmation_sms",
        contentSnippet: confirmBody,
      });

      const message = await createMessage(c.env.DB, {
        id: crypto.randomUUID(),
        threadId,
        direction: "outbound",
        body: confirmBody,
        timestamp: new Date().toISOString(),
        worker: "human_gate",
        confidence: 1.0,
      });

      try {
        await c.env.SMS_QUEUE.send({
          threadId,
          messageId: message.id,
          body: message.body,
          phoneNumber: thread.clientPhone,
          deviceId: c.get("deviceId") as string,
          delaySeconds: 0,
          sequence: 0,
        });
      } catch {
        await writeAuditLog(c.env.DB, {
          threadId,
          action: "sms_queue_failed",
          actor: "system",
          details: { messageId: message.id },
        });
      }
    }
  } else if (decision === "REJECT") {
    const message = await createMessage(c.env.DB, {
      id: crypto.randomUUID(),
      threadId,
      direction: "outbound",
      body: "Hey, sorry but I won't be able to make that work. Wishing you the best! 💕",
      timestamp: new Date().toISOString(),
      worker: "human_gate",
      confidence: 1.0,
    });

    try {
      await c.env.SMS_QUEUE.send({
        threadId,
        messageId: message.id,
        body: message.body,
        phoneNumber: thread.clientPhone,
        deviceId: c.get("deviceId") as string,
        delaySeconds: 0,
        sequence: 0,
      });
    } catch {
      await writeAuditLog(c.env.DB, {
        threadId,
        action: "sms_queue_failed",
        actor: "system",
        details: { messageId: message.id },
      });
    }
  } else {
    // ESCALATE - alert owner (already done via audit log)
    await writeAuditLog(c.env.DB, {
      threadId,
      action: "owner_alert",
      actor: "system",
      details: { reason: "human_escalation", threadId },
    });
  }

  const response: ApiResponse<typeof result.thread> = {
    success: true,
    data: result.thread,
  };

  return c.json(response, 200);
}

// POST /api/threads/:threadId/decision - Submit human decision
threads.post("/:threadId/decision", async (c) => {
  const threadId = c.req.param("threadId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { decision, note, owner, expectedRevision } = body;

  if (!decision || !["APPROVE", "REJECT", "ESCALATE"].includes(decision)) {
    return c.json(
      errorResponse(
        400,
        "BAD_REQUEST",
        "Missing or invalid decision (must be APPROVE, REJECT, or ESCALATE)",
      ),
      400,
    );
  }

  return applyHumanDecision(c as any, threadId, decision, {
    note: note ?? null,
    owner,
    expectedRevision:
      typeof expectedRevision === "number" ? expectedRevision : undefined,
  });
});

// Convenience: POST /api/threads/:threadId/approve|reject|escalate
for (const d of ["approve", "reject", "escalate"] as const) {
  threads.post(`/:threadId/${d}`, async (c) => {
    const threadId = c.req.param("threadId")!;
    const body = await c.req.json().catch(() => ({}));
    const decision = d.toUpperCase() as "APPROVE" | "REJECT" | "ESCALATE";
    return applyHumanDecision(c as any, threadId, decision, {
      note: body?.note ?? null,
      owner: body?.owner,
      expectedRevision:
        typeof body?.expectedRevision === "number"
          ? body.expectedRevision
          : undefined,
    });
  });
}

export default threads;
