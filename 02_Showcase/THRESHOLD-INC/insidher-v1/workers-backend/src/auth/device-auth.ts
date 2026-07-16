import type { Env } from "../types";
import { Errors } from "../errors";
import { writeAuditLog } from "../db/audit";

// ECDSA P-256 device authentication via crypto.subtle.verify()

export interface DeviceAuthHeader {
  deviceId: string;
  timestamp: string;
  nonce: string;
  signature: string; // base64
}

// Parse the X-Device-Auth header or individual headers
export function parseAuthHeaders(headers: Headers): DeviceAuthHeader | null {
  const deviceId =
    headers.get("x-device-id") ?? headers.get("X-Device-Id");
  const timestamp =
    headers.get("x-timestamp") ?? headers.get("X-Timestamp");
  const nonce = headers.get("x-nonce") ?? headers.get("X-Nonce");
  const signature =
    headers.get("x-signature") ?? headers.get("X-Signature");

  if (!deviceId || !timestamp || !nonce || !signature) {
    return null;
  }

  return { deviceId, timestamp, nonce, signature };
}

// Convert base64 string to ArrayBuffer
function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

// Import ECDSA P-256 public key from SPKI base64
async function importPublicKey(spkiBase64: string): Promise<CryptoKey> {
  const keyData = base64ToArrayBuffer(spkiBase64);
  return crypto.subtle.importKey(
    "spki",
    keyData,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
}

// Build the message that was signed: method + path + query + timestamp + nonce + bodyHash
export function buildSignedMessage(
  method: string,
  path: string,
  query: string,
  timestamp: string,
  nonce: string,
  bodyHash: string,
): string {
  return `${method.toUpperCase()}\n${path}\n${query}\n${timestamp}\n${nonce}\n${bodyHash}`;
}

// Compute SHA-256 hash of body, return hex
export async function computeBodyHash(body: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(body);
  const hash = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hash));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Verify device authentication for a request
export async function verifyDeviceAuth(
  db: D1Database,
  auth: DeviceAuthHeader,
  method: string,
  path: string,
  query: string,
  body: string,
): Promise<{ deviceId: string; valid: boolean }> {
  // Check timestamp freshness (5 min window)
  const authTime = parseInt(auth.timestamp, 10);
  const now = Date.now();
  const fiveMinutes = 5 * 60 * 1000;
  if (isNaN(authTime) || Math.abs(now - authTime) > fiveMinutes) {
    await writeAuditLog(db, {
      action: "auth_failure",
      actor: "system",
      details: {
        reason: "timestamp_expired",
        deviceKeyId: auth.deviceId,
      },
    });
    throw Errors.unauthorized("Timestamp expired or invalid");
  }

  // Look up device key
  const deviceKey = await db
    .prepare("SELECT * FROM device_keys WHERE id = ? AND revoked = 0")
    .bind(auth.deviceId)
    .first<Record<string, unknown>>();

  if (!deviceKey) {
    await writeAuditLog(db, {
      action: "auth_failure",
      actor: "system",
      details: {
        reason: "unknown_device_key",
        deviceKeyId: auth.deviceId,
      },
    });
    throw Errors.unauthorized("Unknown device key");
  }

  // Check for replay attack (nonce reuse)
  const seenNonces = deviceKey.seen_nonces
    ? JSON.parse(deviceKey.seen_nonces as string)
    : [];
  if (seenNonces.includes(auth.nonce)) {
    await writeAuditLog(db, {
      action: "auth_failure",
      actor: "system",
      details: {
        reason: "replay_detected",
        deviceKeyId: auth.deviceId,
        nonce: auth.nonce,
      },
    });
    throw Errors.unauthorized("Replay attack detected");
  }

  // Compute body hash and build signed message
  const bodyHash = await computeBodyHash(body);
  const signedMessage = buildSignedMessage(
    method,
    path,
    query,
    auth.timestamp,
    auth.nonce,
    bodyHash,
  );

  // Verify signature
  const publicKey = await importPublicKey(deviceKey.public_key as string);
  const signatureBuffer = base64ToArrayBuffer(auth.signature);
  const encoder = new TextEncoder();
  const messageBuffer = encoder.encode(signedMessage);

  // Use raw ECDSA with SHA-256
  let isValid = false;
  try {
    isValid = await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      publicKey,
      signatureBuffer,
      messageBuffer,
    );
  } catch {
    isValid = false;
  }

  if (!isValid) {
    await writeAuditLog(db, {
      action: "auth_failure",
      actor: "system",
      details: {
        reason: "invalid_signature",
        deviceKeyId: auth.deviceId,
      },
    });
    throw Errors.unauthorized("Invalid signature");
  }

  // Store nonce for replay protection
  seenNonces.push(auth.nonce);
  // Keep only last 100 nonces
  const recentNonces = seenNonces.slice(-100);

  const now_iso = new Date().toISOString();
  await db
    .prepare("UPDATE device_keys SET last_seen_at = ?, seen_nonces = ? WHERE id = ?")
    .bind(now_iso, JSON.stringify(recentNonces), auth.deviceId)
    .run();

  return { deviceId: auth.deviceId, valid: true };
}

// Register a new device key
export async function registerDeviceKey(
  db: D1Database,
  data: {
    deviceId: string;
    publicKey: string;
    deviceName?: string;
  },
): Promise<void> {
  const now = new Date().toISOString();

  // Check if device already exists (rotation)
  const existing = await db
    .prepare("SELECT id FROM device_keys WHERE id = ?")
    .bind(data.deviceId)
    .first();

  if (existing) {
    // Key rotation: revoke old, insert new with same ID by updating
    await db
      .prepare(
        "UPDATE device_keys SET public_key = ?, device_name = ?, registered_at = ?, last_seen_at = ?, revoked = 0, seen_nonces = '[]' WHERE id = ?",
      )
      .bind(data.publicKey, data.deviceName ?? null, now, now, data.deviceId)
      .run();

    await writeAuditLog(db, {
      threadId: null,
      action: "device_key_rotation",
      actor: "system",
      details: { deviceId: data.deviceId },
    });
  } else {
    await db
      .prepare(
        `INSERT INTO device_keys (id, public_key, device_name, registered_at, last_seen_at, revoked, seen_nonces)
         VALUES (?, ?, ?, ?, ?, 0, '[]')`,
      )
      .bind(data.deviceId, data.publicKey, data.deviceName ?? null, now, now)
      .run();

    await writeAuditLog(db, {
      threadId: null,
      action: "device_key_registered",
      actor: "system",
      details: { deviceId: data.deviceId },
    });
  }
}

// Validate that public key is ECDSA P-256
export async function validatePublicKeyFormat(publicKeyBase64: string): Promise<boolean> {
  try {
    const key = await importPublicKey(publicKeyBase64);
    // Check algorithm
    const algo = key.algorithm as { name: string; namedCurve?: string };
    return algo.name === "ECDSA" && algo.namedCurve === "P-256";
  } catch {
    return false;
  }
}

// Check auth failure rate limiting (brute force protection)
export async function checkAuthRateLimit(
  db: D1Database,
  deviceId: string,
): Promise<void> {
  const oneMinuteAgo = new Date(Date.now() - 60 * 1000).toISOString();
  const result = await db
    .prepare(
      "SELECT COUNT(*) as count FROM auth_failures WHERE device_id = ? AND failed_at > ?",
    )
    .bind(deviceId, oneMinuteAgo)
    .first<{ count: number }>();

  if ((result?.count ?? 0) >= 5) {
    throw Errors.rateLimited(60);
  }
}

// Record auth failure
export async function recordAuthFailure(
  db: D1Database,
  deviceId: string,
): Promise<void> {
  await db
    .prepare(
      "INSERT INTO auth_failures (id, device_id, failed_at) VALUES (?, ?, ?)",
    )
    .bind(crypto.randomUUID(), deviceId, new Date().toISOString())
    .run();
}

// Middleware factory for Hono
export function createAuthMiddleware(env: Env) {
  return async (
    c: {
      req: {
        method: string;
        path: string;
        url: string;
        raw: Request;
        header: (name: string) => string | undefined;
      };
      env: Env;
      header: (name: string, value: string) => void;
      set: (key: string, value: unknown) => void;
    },
    next: () => Promise<void>,
  ): Promise<void> => {
    const auth = parseAuthHeaders(c.req.raw.headers as unknown as Headers);
    if (!auth) {
      throw Errors.unauthorized("Missing auth headers");
    }

    // Check rate limit
    await checkAuthRateLimit(env.DB, auth.deviceId);

    const url = new URL(c.req.url);
    const query = url.search;
    const body = await c.req.raw.clone().text();

    try {
      const result = await verifyDeviceAuth(
        env.DB,
        auth,
        c.req.method,
        c.req.path,
        query,
        body,
      );
      c.set("deviceId", result.deviceId);
      await next();
    } catch (err) {
      await recordAuthFailure(env.DB, auth.deviceId);
      throw err;
    }
  };
}
