import { env, SELF } from "cloudflare:test";
import { initDatabase } from "../src/db/init";

let initialized = false;

export async function setupTestDb(): Promise<void> {
  if (initialized) return;
  await initDatabase(env.DB);
  initialized = true;
}

export async function clearTestData(): Promise<void> {
  const tables = [
    "outbound_sms",
    "rate_limits",
    "auth_failures",
    "audit_logs",
    "thread_memory",
    "messages",
    "deposits",
    "threads",
    "personas",
    // Note: device_keys NOT cleared - registered once in beforeAll
  ];
  for (const table of tables) {
    await env.DB.prepare(`DELETE FROM ${table}`).run();
  }
}

// Base URL for all fetch calls (Workers runtime requires absolute URLs)
const BASE_URL = "http://localhost";

// Generate an ECDSA P-256 key pair for testing
export async function generateTestKeyPair(): Promise<{
  publicKey: string;
  privateKey: CryptoKey;
}> {
  const keyPair = (await crypto.subtle.generateKey(
    {
      name: "ECDSA",
      namedCurve: "P-256",
    },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;

  const pubKeyRaw = await crypto.subtle.exportKey(
    "spki",
    keyPair.publicKey,
  );
  const publicKey = btoa(
    String.fromCharCode(...new Uint8Array(pubKeyRaw)),
  );

  return { publicKey, privateKey: keyPair.privateKey };
}

// Sign a message with ECDSA P-256
export async function signMessage(
  privateKey: CryptoKey,
  message: string,
): Promise<string> {
  const data = new TextEncoder().encode(message);
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    privateKey,
    data,
  );
  return btoa(String.fromCharCode(...new Uint8Array(signature)));
}

// Create auth headers for testing
export async function createAuthHeaders(
  privateKey: CryptoKey,
  deviceId: string,
  method: string,
  path: string,
  search: string,
  body: string,
  nonce?: string,
  timestamp?: string,
): Promise<Record<string, string>> {
  // Worker expects epoch milliseconds as the timestamp
  const ts = timestamp ?? String(Date.now());
  const nc = nonce ?? crypto.randomUUID();

  // Compute body hash (SHA-256 hex)
  const bodyData = new TextEncoder().encode(body);
  const bodyHashBuf = await crypto.subtle.digest("SHA-256", bodyData);
  const bodyHash = Array.from(new Uint8Array(bodyHashBuf))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  // Worker uses newlines as separators: METHOD\npath\nquery\ntimestamp\nnonce\nbodyHash
  const signedMessage = `${method.toUpperCase()}\n${path}\n${search}\n${ts}\n${nc}\n${bodyHash}`;
  const signature = await signMessage(privateKey, signedMessage);

  return {
    "X-Device-Id": deviceId,
    "X-Timestamp": ts,
    "X-Nonce": nc,
    "X-Signature": signature,
    "Content-Type": "application/json",
  };
}

// Helper to make authenticated fetch requests
export async function authFetch(
  url: string,
  privateKey: CryptoKey,
  deviceId: string,
  options: { method?: string; body?: string } = {},
): Promise<Response> {
  const method = options.method ?? "GET";
  const body = options.body ?? "";
  const fullUrl = url.startsWith("http") ? url : `${BASE_URL}${url}`;
  const parsedUrl = new URL(fullUrl);
  const path = parsedUrl.pathname;
  const search = parsedUrl.search;

  const headers = await createAuthHeaders(
    privateKey,
    deviceId,
    method,
    path,
    search,
    body,
  );

  return SELF.fetch(fullUrl, {
    method,
    headers,
    body: body || undefined,
  });
}

// Wrapper for unauthenticated fetch calls with absolute URL
export async function apiFetch(
  url: string,
  options: RequestInit = {},
): Promise<Response> {
  const fullUrl = url.startsWith("http") ? url : `${BASE_URL}${url}`;
  return SELF.fetch(fullUrl, options);
}
