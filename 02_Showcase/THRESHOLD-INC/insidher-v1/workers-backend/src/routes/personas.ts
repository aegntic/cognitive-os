import { Hono } from "hono";
import type { Env, ApiResponse } from "../types";
import { ApiError, errorResponse, asStatusCode } from "../errors";
import {
  createPersona,
  getPersonaOrNull,
  updatePersona,
  listPersonas,
} from "../db/personas";

const personas = new Hono<{ Bindings: Env }>();

// POST /api/personas - Create a persona
personas.post("/", async (c) => {
  const body = await c.req.json().catch(() => null);
  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { name, tone, vocabulary, offerings, depositWording, boundaries, availabilityPolicy } = body;

  if (!name || typeof name !== "string") {
    return c.json(errorResponse(400, "BAD_REQUEST", "Missing or invalid name"), 400);
  }

  if (!tone || typeof tone !== "string") {
    return c.json(errorResponse(400, "BAD_REQUEST", "Missing or invalid tone"), 400);
  }

  if (!availabilityPolicy || typeof availabilityPolicy !== "object") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid availabilityPolicy"),
      400,
    );
  }

  // Validate availabilityPolicy is valid JSON (it must be an object)
  try {
    JSON.stringify(availabilityPolicy);
  } catch {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "availabilityPolicy must be valid JSON"),
      400,
    );
  }

  const personaId = crypto.randomUUID();
  const persona = await createPersona(c.env.DB, {
    id: personaId,
    name,
    tone,
    vocabulary: vocabulary ?? [],
    offerings: offerings ?? [],
    depositWording: depositWording ?? null,
    boundaries: boundaries ?? null,
    availabilityPolicy,
  });

  const response: ApiResponse<typeof persona> = {
    success: true,
    data: persona,
  };

  return c.json(response, 201);
});

// GET /api/personas - List all personas
personas.get("/", async (c) => {
  const personaList = await listPersonas(c.env.DB);

  const response: ApiResponse<typeof personaList> = {
    success: true,
    data: personaList,
  };

  return c.json(response, 200);
});

// GET /api/personas/:personaId - Get a persona
personas.get("/:personaId", async (c) => {
  const personaId = c.req.param("personaId")!;
  const persona = await getPersonaOrNull(c.env.DB, personaId);

  if (!persona) {
    return c.json(errorResponse(404, "NOT_FOUND", "Persona not found"), 404);
  }

  const response: ApiResponse<typeof persona> = {
    success: true,
    data: persona,
  };

  return c.json(response, 200);
});

// PATCH /api/personas/:personaId - Update a persona
personas.patch("/:personaId", async (c) => {
  const personaId = c.req.param("personaId")!;
  const body = await c.req.json().catch(() => null);

  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  // Verify persona exists
  const existing = await getPersonaOrNull(c.env.DB, personaId);
  if (!existing) {
    return c.json(errorResponse(404, "NOT_FOUND", "Persona not found"), 404);
  }

  const updates: Record<string, unknown> = {};
  const allowedFields = [
    "name",
    "tone",
    "vocabulary",
    "offerings",
    "depositWording",
    "boundaries",
    "availabilityPolicy",
  ];

  for (const field of allowedFields) {
    if (field in body) {
      updates[field] = body[field];
    }
  }

  try {
    const persona = await updatePersona(c.env.DB, personaId, updates);

    const response: ApiResponse<typeof persona> = {
      success: true,
      data: persona,
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

export default personas;
