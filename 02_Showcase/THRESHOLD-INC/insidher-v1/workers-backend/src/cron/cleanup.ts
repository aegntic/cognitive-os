import type { Env } from "../types";
import { expireStaleDeposits } from "../db/deposits";

// Cleanup cron handler (runs hourly)
// Expires stale pending deposits and cleans up old data
export async function handleCleanup(env: Env): Promise<void> {
  const ttlHours = parseInt(env.CLEANUP_TTL_HOURS ?? "24", 10);
  const threshold = new Date(Date.now() - ttlHours * 60 * 60 * 1000).toISOString();

  // Mark old PENDING/RECEIVED deposits as FAILED (evidence timeout) with per-deposit audit
  await expireStaleDeposits(env.DB, threshold);

  // Clean up old delivered outbound SMS (> 7 days)
  const sevenDaysAgo = new Date(
    Date.now() - 7 * 24 * 60 * 60 * 1000,
  ).toISOString();

  await env.DB.prepare(
    "DELETE FROM outbound_sms WHERE delivered = 1 AND delivered_at < ?",
  )
    .bind(sevenDaysAgo)
    .run();

  // Clean up old auth failures (> 1 hour)
  const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  await env.DB.prepare("DELETE FROM auth_failures WHERE failed_at < ?")
    .bind(oneHourAgo)
    .run();
}
