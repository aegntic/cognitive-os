// D1 initialization helper - runs schema on a fresh database
import { SCHEMA_SQL } from "./schema-content";

export async function initDatabase(db: D1Database): Promise<void> {
  // Split schema on semicolons and execute each statement
  const statements = SCHEMA_SQL
    .split(";")
    .map((s) => s.trim())
    .filter((s) => s.length > 0 && !s.startsWith("--"));

  const batches: D1PreparedStatement[] = [];
  for (const stmt of statements) {
    batches.push(db.prepare(stmt));
  }
  await db.batch(batches);
}

export async function ensureSchema(db: D1Database): Promise<void> {
  try {
    // Check if threads table exists
    const result = await db
      .prepare(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='threads'",
      )
      .first();
    if (!result) {
      await initDatabase(db);
    }
  } catch {
    await initDatabase(db);
  }
}
