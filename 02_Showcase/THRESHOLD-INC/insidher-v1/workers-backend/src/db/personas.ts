import type { PersonaProfile } from "../types";
import { Errors } from "../errors";

export interface PersonaRow {
  id: string;
  name: string;
  tone: string;
  vocabulary: string[];
  offerings: string[];
  depositWording: string | null;
  boundaries: string[] | null;
  availabilityPolicy: PersonaProfile["availabilityPolicy"];
  createdAt: string;
  updatedAt: string;
}

function rowToPersona(row: Record<string, unknown>): PersonaRow {
  return {
    id: row.id as string,
    name: row.name as string,
    tone: row.tone as string,
    vocabulary: row.vocabulary ? JSON.parse(row.vocabulary as string) : [],
    offerings: row.offerings ? JSON.parse(row.offerings as string) : [],
    depositWording: (row.deposit_wording as string) ?? null,
    boundaries: row.boundaries ? JSON.parse(row.boundaries as string) : null,
    availabilityPolicy: row.availability_policy
      ? JSON.parse(row.availability_policy as string)
      : {
          timezone: "Australia/Sydney",
          weeklyWindows: {},
          dndPeriods: [],
          dateOverrides: {},
        },
    createdAt: row.created_at as string,
    updatedAt: row.updated_at as string,
  };
}

export async function createPersona(
  db: D1Database,
  data: {
    id: string;
    name: string;
    tone: string;
    vocabulary?: string[];
    offerings?: string[];
    depositWording?: string | null;
    boundaries?: string[] | null;
    availabilityPolicy: PersonaProfile["availabilityPolicy"];
  },
): Promise<PersonaRow> {
  const now = new Date().toISOString();

  await db
    .prepare(
      `INSERT INTO personas (id, name, tone, vocabulary, offerings, deposit_wording, boundaries, availability_policy, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      data.id,
      data.name,
      data.tone,
      JSON.stringify(data.vocabulary ?? []),
      JSON.stringify(data.offerings ?? []),
      data.depositWording ?? null,
      JSON.stringify(data.boundaries ?? []),
      JSON.stringify(data.availabilityPolicy),
      now,
      now,
    )
    .run();

  return getPersona(db, data.id);
}

export async function getPersona(db: D1Database, personaId: string): Promise<PersonaRow> {
  const row = await db
    .prepare("SELECT * FROM personas WHERE id = ?")
    .bind(personaId)
    .first();
  if (!row) throw Errors.notFound("Persona not found");
  return rowToPersona(row);
}

export async function getPersonaOrNull(
  db: D1Database,
  personaId: string,
): Promise<PersonaRow | null> {
  const row = await db
    .prepare("SELECT * FROM personas WHERE id = ?")
    .bind(personaId)
    .first();
  if (!row) return null;
  return rowToPersona(row);
}

export async function updatePersona(
  db: D1Database,
  personaId: string,
  updates: Partial<{
    name: string;
    tone: string;
    vocabulary: string[];
    offerings: string[];
    depositWording: string | null;
    boundaries: string[] | null;
    availabilityPolicy: PersonaProfile["availabilityPolicy"];
  }>,
): Promise<PersonaRow> {
  const existing = await getPersona(db, personaId);
  const now = new Date().toISOString();

  const fields: string[] = ["updated_at = ?"];
  const values: (string | null)[] = [now];

  const fieldMap: Record<string, [string, () => string | null]> = {
    name: ["name", () => updates.name!],
    tone: ["tone", () => updates.tone!],
    vocabulary: ["vocabulary", () => JSON.stringify(updates.vocabulary!)],
    offerings: ["offerings", () => JSON.stringify(updates.offerings!)],
    depositWording: ["deposit_wording", () => updates.depositWording ?? null],
    boundaries: ["boundaries", () => JSON.stringify(updates.boundaries!)],
    availabilityPolicy: [
      "availability_policy",
      () => JSON.stringify(updates.availabilityPolicy!),
    ],
  };

  for (const [key, [col, getter]] of Object.entries(fieldMap)) {
    if (key in updates && updates[key as keyof typeof updates] !== undefined) {
      fields.push(`${col} = ?`);
      values.push(getter());
    }
  }

  await db
    .prepare(`UPDATE personas SET ${fields.join(", ")} WHERE id = ?`)
    .bind(...values, personaId)
    .run();

  void existing; // reference to ensure persona exists
  return getPersona(db, personaId);
}

export async function listPersonas(db: D1Database): Promise<PersonaRow[]> {
  const result = await db
    .prepare("SELECT * FROM personas ORDER BY created_at DESC")
    .all();
  const rows = (result.results ?? []) as unknown as Record<string, unknown>[];
  return rows.map(rowToPersona);
}
