import { Hono } from "hono";
import type { Env, ApiResponse, DepositStatus, EvidenceType } from "../types";
import { ApiError, errorResponse, asStatusCode } from "../errors";
import { getThreadOrNull } from "../db/threads";
import {
  createDeposit,
  getDeposit,
  listDeposits,
  updateDepositStatus,
  attachEvidence,
  failDeposit,
  retryDeposit,
} from "../db/deposits";
import { logSafetyCheck } from "../db/audit";

const deposits = new Hono<{ Bindings: Env; Variables: { deviceId: string } }>();

// POST /api/threads/:threadId/deposits - Create a deposit
deposits.post("/", async (c) => {
  const threadId = c.req.param("threadId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { amount, currency } = body;

  if (typeof amount !== "number" || amount < 0) {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid amount"),
      400,
    );
  }

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const deviceId = c.get("deviceId") as string | undefined;
  const depositId = crypto.randomUUID();
  const deposit = await createDeposit(c.env.DB, {
    id: depositId,
    threadId,
    amount,
    currency: currency ?? c.env.DEFAULT_DEPOSIT_CURRENCY ?? "AUD",
    actor: deviceId ?? "system",
  });

  await logSafetyCheck(c.env.DB, {
    threadId,
    verdict: "SAFE",
    direction: "inbound",
    context: "deposit_create",
    contentSnippet: `amount=${amount}`,
  });

  const response: ApiResponse<typeof deposit> = {
    success: true,
    data: deposit,
  };

  return c.json(response, 201);
});

// GET /api/threads/:threadId/deposits - List deposits for a thread
deposits.get("/", async (c) => {
  const threadId = c.req.param("threadId")!;

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const depositList = await listDeposits(c.env.DB, threadId);

  const response: ApiResponse<typeof depositList> = {
    success: true,
    data: depositList,
  };

  return c.json(response, 200);
});

// GET /api/threads/:threadId/deposits/:depositId
deposits.get("/:depositId", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deposit = await getDeposit(c.env.DB, threadId, depositId);
    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

// POST /api/threads/:threadId/deposits/:depositId/evidence - Upload evidence
deposits.post("/:depositId/evidence", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { evidenceType, evidenceRef } = body;
  if (!evidenceType || !evidenceRef) {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "evidenceType and evidenceRef required"),
      400,
    );
  }

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deviceId = c.get("deviceId") as string | undefined;
    const deposit = await attachEvidence(
      c.env.DB,
      threadId,
      depositId,
      { type: evidenceType as EvidenceType, ref: evidenceRef },
      deviceId ?? "system",
    );

    await logSafetyCheck(c.env.DB, {
      threadId,
      verdict: "SAFE",
      direction: "inbound",
      context: "deposit_evidence",
      contentSnippet: `${evidenceType}:${evidenceRef}`,
    });

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

// POST /api/threads/:threadId/deposits/:depositId/verify - Verify with evidence required
deposits.post("/:depositId/verify", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;
  const body = await c.req.json().catch(() => ({}));

  const evidenceType = body?.evidenceType as string | undefined;
  const evidenceRef = body?.evidenceRef as string | undefined;

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    // Ensure evidence is present either in body or already attached
    const existing = await getDeposit(c.env.DB, threadId, depositId);
    const type = (evidenceType ?? existing.evidenceType) as EvidenceType | null;
    const ref = evidenceRef ?? existing.evidenceRef;

    if (!type || !ref) {
      return c.json(
        errorResponse(400, "BAD_REQUEST", "evidenceRef required for VERIFIED status"),
        400,
      );
    }

    // If still PENDING, step through RECEIVED first (contract transitions)
    if (existing.status === "PENDING") {
      await updateDepositStatus(c.env.DB, threadId, depositId, "RECEIVED", {
        type,
        ref,
      }, { actor: (c.get("deviceId") as string) ?? "system", advanceThread: false });
    }

    const deviceId = c.get("deviceId") as string | undefined;
    const deposit = await updateDepositStatus(
      c.env.DB,
      threadId,
      depositId,
      "VERIFIED",
      { type, ref },
      { actor: deviceId ?? "system", advanceThread: true },
    );

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

// POST /api/threads/:threadId/deposits/:depositId/timeout - Evidence timeout → FAILED
deposits.post("/:depositId/timeout", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deviceId = c.get("deviceId") as string | undefined;
    const deposit = await failDeposit(
      c.env.DB,
      threadId,
      depositId,
      "evidence_timeout",
      deviceId ?? "system",
    );

    await logSafetyCheck(c.env.DB, {
      threadId,
      verdict: "SAFE",
      direction: "inbound",
      context: "deposit_timeout",
      contentSnippet: depositId,
    });

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

// POST /api/threads/:threadId/deposits/:depositId/decline - Client decline → FAILED
deposits.post("/:depositId/decline", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;
  const body = await c.req.json().catch(() => ({}));

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deviceId = c.get("deviceId") as string | undefined;
    const deposit = await failDeposit(
      c.env.DB,
      threadId,
      depositId,
      "client_decline",
      deviceId ?? "system",
    );

    await logSafetyCheck(c.env.DB, {
      threadId,
      verdict: "SAFE",
      direction: "inbound",
      context: "client_decline",
      contentSnippet: body?.note ?? depositId,
    });

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

// POST /api/threads/:threadId/deposits/:depositId/retry - New deposit after FAILED
deposits.post("/:depositId/retry", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;
  const body = await c.req.json().catch(() => ({}));

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deviceId = c.get("deviceId") as string | undefined;
    const deposit = await retryDeposit(c.env.DB, threadId, depositId, {
      amount: typeof body?.amount === "number" ? body.amount : undefined,
      currency: typeof body?.currency === "string" ? body.currency : undefined,
      actor: deviceId ?? "system",
    });

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
    };
    return c.json(response, 201);
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

// PATCH /api/threads/:threadId/deposits/:depositId - Update deposit status
deposits.patch("/:depositId", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { status, evidenceType, evidenceRef } = body;

  if (!status || typeof status !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid status"),
      400,
    );
  }

  if (status === "VERIFIED") {
    if (!evidenceRef || typeof evidenceRef !== "string") {
      // Allow if evidence already attached
      try {
        const existing = await getDeposit(c.env.DB, threadId, depositId);
        if (!existing.evidenceRef) {
          return c.json(
            errorResponse(400, "BAD_REQUEST", "evidenceRef required for VERIFIED status"),
            400,
          );
        }
      } catch (err) {
        if (err instanceof ApiError) {
          return c.json(
            errorResponse(err.statusCode, err.code, err.message),
            asStatusCode(err.statusCode),
          );
        }
        throw err;
      }
    }
  }

  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const deviceId = c.get("deviceId") as string | undefined;
    let evidence:
      | { type: EvidenceType; ref: string }
      | undefined;

    if (evidenceType && evidenceRef) {
      evidence = { type: evidenceType as EvidenceType, ref: evidenceRef };
    } else if (status === "VERIFIED") {
      const existing = await getDeposit(c.env.DB, threadId, depositId);
      if (existing.evidenceType && existing.evidenceRef) {
        evidence = {
          type: existing.evidenceType as EvidenceType,
          ref: existing.evidenceRef,
        };
      }
    }

    const deposit = await updateDepositStatus(
      c.env.DB,
      threadId,
      depositId,
      status as DepositStatus,
      evidence,
      {
        actor: deviceId ?? "system",
        reason: body.reason,
        advanceThread: status === "VERIFIED",
      },
    );

    const response: ApiResponse<typeof deposit> = {
      success: true,
      data: deposit,
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

export default deposits;
