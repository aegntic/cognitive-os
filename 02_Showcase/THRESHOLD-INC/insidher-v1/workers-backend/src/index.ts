import { Hono } from "hono";
import { cors } from "hono/cors";
import type { Env, ApiResponse } from "./types";
import { errorHandler, errorResponse, ApiError, asStatusCode } from "./errors";
import { ensureSchema } from "./db/init";
import {
  parseAuthHeaders,
  verifyDeviceAuth,
  checkAuthRateLimit,
  recordAuthFailure,
} from "./auth/device-auth";

// Route modules
import authRoutes from "./routes/auth";
import threadRoutes from "./routes/threads";
import personaRoutes from "./routes/personas";
import smsRoutes from "./routes/sms";
import depositsRoutes from "./routes/deposits";
import pollingRoutes from "./routes/polling";

// Queue handlers
import { handleLlmQueue } from "./queues/llm-queue";
import { handleSmsQueue } from "./queues/sms-queue";

// Cron handlers
import { handleStalledThreads } from "./cron/stalled-threads";
import { handleCleanup } from "./cron/cleanup";

// App type with Bindings and Variables
type AppType = { Bindings: Env; Variables: { deviceId: string } };

// Create Hono app
const app = new Hono<AppType>();

// CORS middleware
app.use(
  "*",
  cors({
    origin: ["https://insidher.app", "http://localhost:*"],
    allowMethods: ["GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"],
    allowHeaders: [
      "Content-Type",
      "Authorization",
      "X-Device-Id",
      "X-Timestamp",
      "X-Nonce",
      "X-Signature",
    ],
    exposeHeaders: ["Retry-After", "Allow"],
    maxAge: 86400,
  }),
);

// Health check (no auth required)
app.get("/health", async (c) => {
  const response: ApiResponse<{ status: string; timestamp: string }> = {
    success: true,
    data: { status: "healthy", timestamp: new Date().toISOString() },
  };
  return c.json(response, 200);
});

// SMS webhook (no auth required - Twilio/webhook calls)
app.route("/webhook/sms", smsRoutes);

// Auth routes (register is public, verify tests auth)
app.route("/api/auth", authRoutes);

// Auth middleware for protected routes
app.use("/api/*", async (c, next) => {
  // Skip auth for /api/auth/register
  if (c.req.path === "/api/auth/register") {
    return next();
  }

  const authHeaders = parseAuthHeaders(
    c.req.raw.headers as unknown as Headers,
  );

  if (!authHeaders) {
    return c.json(
      errorResponse(401, "UNAUTHORIZED", "Authentication required"),
      401,
    );
  }

  // Check rate limit for auth
  try {
    await checkAuthRateLimit(c.env.DB, authHeaders.deviceId);
  } catch (err) {
    if (err instanceof ApiError) {
      return c.json(
        errorResponse(err.statusCode, err.code, err.message),
        asStatusCode(err.statusCode),
      );
    }
    throw err;
  }

  const url = new URL(c.req.url);
  const body = await c.req.raw.clone().text();

  try {
    const result = await verifyDeviceAuth(
      c.env.DB,
      authHeaders,
      c.req.method,
      c.req.path,
      url.search,
      body,
    );
    c.set("deviceId", result.deviceId);
    await next();
  } catch (err) {
    await recordAuthFailure(c.env.DB, authHeaders.deviceId);
    if (err instanceof ApiError) {
      return c.json(
        errorResponse(err.statusCode, err.code, err.message),
        asStatusCode(err.statusCode),
      );
    }
    throw err;
  }
});

// Protected API routes
app.route("/api/threads", threadRoutes);
app.route("/api/personas", personaRoutes);
app.route("/api/devices", pollingRoutes);

// Nested deposit routes (mounted under /api/threads/:threadId/deposits)
app.route("/api/threads/:threadId/deposits", depositsRoutes);

// 404 handler for unknown routes
app.notFound((c) => {
  return c.json(
    errorResponse(404, "NOT_FOUND", `Route not found: ${c.req.method} ${c.req.path}`),
    404,
  );
});

// Error handler
app.onError(errorHandler);

// Export the Hono app as default with queue and scheduled handlers
export default {
  // ponytail: init schema on first HTTP too (was queue/cron-only; empty D1 500'd SMS)
  async fetch(req: Request, env: Env, ctx: ExecutionContext) {
    await ensureSchema(env.DB);
    return app.fetch(req, env, ctx);
  },

  // Queue consumer handlers
  async queue(
    batch: MessageBatch,
    env: Env,
  ): Promise<void> {
    // Initialize schema if needed
    await ensureSchema(env.DB);

    if (batch.queue === "llm-queue") {
      await handleLlmQueue(
        batch as MessageBatch<import("./types").LLMQueueMessage>,
        env,
      );
    } else if (batch.queue === "sms-queue") {
      await handleSmsQueue(
        batch as MessageBatch<import("./types").SMSQueueMessage>,
        env,
      );
    }
  },

  // Scheduled (cron) handler
  async scheduled(
    event: ScheduledEvent,
    env: Env,
    ctx: ExecutionContext,
  ): Promise<void> {
    // Initialize schema if needed
    await ensureSchema(env.DB);

    ctx.waitUntil(
      (async () => {
        try {
          const cron = event.cron;
          if (cron === "*/5 * * * *") {
            await handleStalledThreads(env);
          } else if (cron === "0 * * * *") {
            await handleCleanup(env);
          }
        } catch (err) {
          console.error("Cron error:", err);
          await writeAuditLogSafe(env, {
            action: "cron_error",
            actor: "system",
            details: {
              cron: event.cron,
              error: err instanceof Error ? err.message : String(err),
            },
          });
        }
      })(),
    );
  },
};

// Safe audit log helper that doesn't throw
async function writeAuditLogSafe(
  env: Env,
  data: {
    threadId?: string | null;
    action: string;
    actor: string;
    details?: Record<string, unknown> | null;
  },
): Promise<void> {
  try {
    const now = new Date().toISOString();
    await env.DB.prepare(
      "INSERT INTO audit_logs (id, thread_id, action, actor, details, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
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
  } catch (e) {
    console.error("Failed to write cron error audit log:", e);
  }
}
