import { Hono } from "hono";
import type { Env, ApiResponse, DepositStatus, EvidenceType } from "../types";
import { ApiError, errorResponse, asStatusCode } from "../errors";
import { getThreadOrNull } from "../db/threads";
import {
  createDeposit,
  getDeposit,
  listDeposits,
  updateDepositStatus,
} from "../db/deposits";

const deposits = new Hono<{ Bindings: Env }>();

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

  // Verify thread exists
  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  const depositId = crypto.randomUUID();
  const deposit = await createDeposit(c.env.DB, {
    id: depositId,
    threadId,
    amount,
    currency: currency ?? c.env.DEFAULT_DEPOSIT_CURRENCY ?? "AUD",
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

  // Verify thread exists
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

// GET /api/threads/:threadId/deposits/:depositId - Get a specific deposit
deposits.get("/:depositId", async (c) => {
  const threadId = c.req.param("threadId")!;
  const depositId = c.req.param("depositId")!;

  // Verify thread exists
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

  // If verifying, require evidence
  if (status === "VERIFIED") {
    if (!evidenceRef || typeof evidenceRef !== "string") {
      return c.json(
        errorResponse(400, "BAD_REQUEST", "evidenceRef required for VERIFIED status"),
        400,
      );
    }
  }

  // Verify thread exists
  const thread = await getThreadOrNull(c.env.DB, threadId);
  if (!thread) {
    return c.json(errorResponse(404, "NOT_FOUND", "Thread not found"), 404);
  }

  try {
    const evidence =
      status === "VERIFIED" && evidenceType && evidenceRef
        ? { type: evidenceType as EvidenceType, ref: evidenceRef }
        : undefined;

    const deposit = await updateDepositStatus(
      c.env.DB,
      threadId,
      depositId,
      status as DepositStatus,
      evidence,
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
