import { Hono } from "hono";
import type { Env, ApiResponse } from "../types";
import { ApiError, errorResponse, asStatusCode } from "../errors";
import {
  registerDeviceKey,
  validatePublicKeyFormat,
  verifyDeviceAuth,
  parseAuthHeaders,
  checkAuthRateLimit,
  recordAuthFailure,
} from "../auth/device-auth";

const auth = new Hono<{ Bindings: Env }>();

// POST /api/auth/register - Register device public key (no auth required)
auth.post("/register", async (c) => {
  const body = await c.req.json().catch(() => null);
  if (!body) {
    return c.json(errorResponse(400, "BAD_REQUEST", "Invalid JSON body"), 400);
  }

  const { deviceId, publicKey, deviceName } = body;

  if (!deviceId || typeof deviceId !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid deviceId"),
      400,
    );
  }

  if (!publicKey || typeof publicKey !== "string") {
    return c.json(
      errorResponse(400, "BAD_REQUEST", "Missing or invalid publicKey"),
      400,
    );
  }

  // Validate that the key is ECDSA P-256
  const isValid = await validatePublicKeyFormat(publicKey);
  if (!isValid) {
    return c.json(
      errorResponse(
        400,
        "INVALID_KEY_TYPE",
        "Public key must be ECDSA P-256 in SPKI base64 format",
      ),
      400,
    );
  }

  await registerDeviceKey(c.env.DB, {
    deviceId,
    publicKey,
    deviceName: deviceName ?? undefined,
  });

  const response: ApiResponse<{ deviceId: string; registered: boolean }> = {
    success: true,
    data: { deviceId, registered: true },
  };

  return c.json(response, 201);
});

// POST /api/auth/verify - Verify a signed request (test endpoint for auth)
auth.post("/verify", async (c) => {
  const authHeaders = parseAuthHeaders(
    c.req.raw.headers as unknown as Headers,
  );
  if (!authHeaders) {
    return c.json(
      errorResponse(401, "UNAUTHORIZED", "Missing auth headers"),
      401,
    );
  }

  // Check rate limit for auth attempts
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

    const response: ApiResponse<{ deviceId: string; valid: boolean }> = {
      success: true,
      data: result,
    };
    return c.json(response, 200);
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

export default auth;
