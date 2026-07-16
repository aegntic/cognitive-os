import type { Env } from "../types";
import { ACTIVE_STATES } from "../types";
import { transitionState } from "../db/threads";
import { writeAuditLog } from "../db/audit";

// Stalled threads cron handler (runs every 5 minutes)
export async function handleStalledThreads(env: Env): Promise<void> {
  const stallThresholdMinutes = parseInt(
    env.STALL_THRESHOLD_MINUTES ?? "30",
    10,
  );
  const threshold = new Date(
    Date.now() - stallThresholdMinutes * 60 * 1000,
  ).toISOString();

  // Find active threads with stale last_message_at
  const placeholders = ACTIVE_STATES.map(() => "?").join(",");
  const result = await env.DB.prepare(
    `SELECT * FROM threads
     WHERE state IN (${placeholders})
     AND last_message_at IS NOT NULL
     AND last_message_at < ?
     AND updated_at < ?`,
  )
    .bind(...ACTIVE_STATES, threshold, threshold)
    .all();

  const stalledThreads = (result.results ?? []) as unknown as Array<
    Record<string, unknown>
  >;

  for (const thread of stalledThreads) {
    const threadId = thread.id as string;
    const revision = thread.revision as number;
    const currentState = thread.state as string;

    try {
      const result = await transitionState(
        env.DB,
        threadId,
        "STALLED",
        revision,
        "system",
        { reason: "inactivity" },
      );

      if (result.success) {
        await writeAuditLog(env.DB, {
          threadId,
          action: "stalled_detected",
          actor: "system",
          details: {
            from: currentState,
            to: "STALLED",
            reason: "inactivity",
            thresholdMinutes: stallThresholdMinutes,
          },
        });
      }
    } catch {
      // Thread may have transitioned already or invalid transition
    }
  }
}
