import type { Env } from "../types";
import { Errors } from "../errors";

// Simple rate limiter using D1 rate_limits table
// Tracks requests per identifier (device ID or IP) within a 1-minute window

export async function checkRateLimit(
  db: D1Database,
  identifier: string,
  limit: number = 20,
): Promise<void> {
  const now = new Date();
  const windowStart = new Date(
    now.getTime() - 60 * 1000,
  ).toISOString();

  // Clean old entries and count current window
  const result = await db
    .prepare(
      `SELECT count, window_start FROM rate_limits WHERE identifier = ?`,
    )
    .bind(identifier)
    .first<{ count: number; window_start: string }>();

  if (result) {
    const windowDate = new Date(result.window_start);
    if (windowDate > new Date(windowStart)) {
      // Within current window
      if (result.count >= limit) {
        throw Errors.rateLimited(60);
      }
      // Increment counter
      await db
        .prepare(
          "UPDATE rate_limits SET count = count + 1 WHERE identifier = ?",
        )
        .bind(identifier)
        .run();
    } else {
      // Window expired, reset
      await db
        .prepare(
          "UPDATE rate_limits SET count = 1, window_start = ? WHERE identifier = ?",
        )
        .bind(now.toISOString(), identifier)
        .run();
    }
  } else {
    // New entry
    await db
      .prepare(
        "INSERT INTO rate_limits (id, identifier, window_start, count) VALUES (?, ?, ?, 1)",
      )
      .bind(crypto.randomUUID(), identifier, now.toISOString())
      .run();
  }
}

// Hono middleware factory
export function rateLimitMiddleware(env: Env, limit: number = 20) {
  return async (
    c: {
      req: { header: (name: string) => string | undefined; raw: Request };
      set: (key: string, value: unknown) => void;
    },
    next: () => Promise<void>,
  ): Promise<void> => {
    // Get identifier from device auth header or IP
    const deviceId = c.req.header("x-device-id");
    const ip = c.req.raw.headers.get("CF-Connecting-IP") ?? "unknown";
    const identifier = deviceId ?? ip;

    await checkRateLimit(env.DB, identifier, limit);
    await next();
  };
}
