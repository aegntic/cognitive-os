import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import {
  setupTestDb,
  clearTestData,
  generateTestKeyPair,
  createAuthHeaders,
} from "./helpers";

describe("Device Auth API", () => {
  let testKeys: { publicKey: string; privateKey: CryptoKey };

  beforeAll(async () => {
    await setupTestDb();
    testKeys = await generateTestKeyPair();
  });

  beforeEach(async () => {
    await clearTestData();
  });

  describe("POST /api/auth/register", () => {
    it("should register a valid ECDSA P-256 public key", async () => {
      const res = await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "test-device-1",
          publicKey: testKeys.publicKey,
          deviceName: "Test Phone",
        }),
      });

      expect(res.status).toBe(201);
      const body = await res.json();
      expect(body.success).toBe(true);
      expect(body.data.deviceId).toBe("test-device-1");
      expect(body.data.registered).toBe(true);
    });

    it("should return 400 for missing deviceId", async () => {
      const res = await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          publicKey: testKeys.publicKey,
        }),
      });

      expect(res.status).toBe(400);
      const body = await res.json();
      expect(body.success).toBe(false);
      expect(body.error.code).toBe("BAD_REQUEST");
    });

    it("should return 400 for missing publicKey", async () => {
      const res = await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "test-device-2",
        }),
      });

      expect(res.status).toBe(400);
      const body = await res.json();
      expect(body.success).toBe(false);
    });

    it("should return 400 for invalid key type (not ECDSA P-256)", async () => {
      // Generate an RSA key instead
      const rsaKey = (await crypto.subtle.generateKey(
        { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
        true,
        ["sign", "verify"],
      )) as CryptoKeyPair;
      const rsaPub = await crypto.subtle.exportKey("spki", rsaKey.publicKey);
      const rsaPubB64 = btoa(String.fromCharCode(...new Uint8Array(rsaPub)));

      const res = await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "rsa-device",
          publicKey: rsaPubB64,
        }),
      });

      expect(res.status).toBe(400);
      const body = await res.json();
      expect(body.error.code).toBe("INVALID_KEY_TYPE");
    });

    it("should not require auth headers", async () => {
      const res = await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "no-auth-device",
          publicKey: testKeys.publicKey,
        }),
      });

      expect(res.status).toBe(201);
    });
  });

  describe("Protected routes require auth", () => {
    it("should return 401 without auth headers", async () => {
      const res = await SELF.fetch("http://localhost/api/threads", {
        method: "GET",
      });

      expect(res.status).toBe(401);
      const body = await res.json();
      expect(body.success).toBe(false);
      expect(body.error.code).toBe("UNAUTHORIZED");
    });

    it("should return 401 with missing auth headers", async () => {
      const res = await SELF.fetch("http://localhost/api/threads", {
        method: "GET",
        headers: {
          "X-Device-Id": "test-device",
          // Missing other headers
        },
      });

      expect(res.status).toBe(401);
    });

    it("should return 401 with invalid signature", async () => {
      // Register device first
      await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "auth-device",
          publicKey: testKeys.publicKey,
        }),
      });

      const res = await SELF.fetch("http://localhost/api/threads", {
        method: "GET",
        headers: {
          "X-Device-Id": "auth-device",
          "X-Timestamp": new Date().toISOString(),
          "X-Nonce": crypto.randomUUID(),
          "X-Signature": "invalid-signature",
        },
      });

      expect(res.status).toBe(401);
    });

    it("should return 401 with stale timestamp", async () => {
      // Register device first
      await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "stale-device",
          publicKey: testKeys.publicKey,
        }),
      });

      const staleTimestamp = new Date(Date.now() - 10 * 60 * 1000).toISOString();
      const headers = await createAuthHeaders(
        testKeys.privateKey,
        "stale-device",
        "GET",
        "/api/threads",
        "",
        "",
        crypto.randomUUID(),
        staleTimestamp,
      );

      const res = await SELF.fetch("http://localhost/api/threads", { headers });
      expect(res.status).toBe(401);
    });

    it("should accept valid auth and return 200", async () => {
      // Register device
      await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "valid-device",
          publicKey: testKeys.publicKey,
        }),
      });

      const headers = await createAuthHeaders(
        testKeys.privateKey,
        "valid-device",
        "GET",
        "/api/threads",
        "",
        "",
      );

      const res = await SELF.fetch("http://localhost/api/threads", { headers });
      expect(res.status).toBe(200);
    });
  });

  describe("Replay protection", () => {
    it("should reject replayed nonce", async () => {
      // Register device
      await SELF.fetch("http://localhost/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: "replay-device",
          publicKey: testKeys.publicKey,
        }),
      });

      const nonce = crypto.randomUUID();
      const headers = await createAuthHeaders(
        testKeys.privateKey,
        "replay-device",
        "GET",
        "/api/threads",
        "",
        "",
        nonce,
      );

      // First request should succeed
      const res1 = await SELF.fetch("http://localhost/api/threads", { headers });
      expect(res1.status).toBe(200);

      // Replay should fail
      const res2 = await SELF.fetch("http://localhost/api/threads", { headers });
      expect(res2.status).toBe(401);
    });
  });
});
