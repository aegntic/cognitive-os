import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import {
  setupTestDb,
  clearTestData,
  generateTestKeyPair,
  authFetch,
} from "./helpers";

describe("Deposits API", () => {
  let testKeys: { publicKey: string; privateKey: CryptoKey };
  const deviceId = "deposits-test-device";

  beforeAll(async () => {
    await setupTestDb();
    testKeys = await generateTestKeyPair();

    await SELF.fetch("http://localhost/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        deviceId,
        publicKey: testKeys.publicKey,
      }),
    });
  });

  beforeEach(async () => {
    await clearTestData();

    // Create persona and thread for tests
    const now = new Date().toISOString();
    await env.DB.prepare(
      "INSERT INTO personas (id, name, tone, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
    ).bind("dep-persona", "Test", "warm", now, now).run();

    await env.DB.prepare(
      "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind("dep-thread", "dep-persona", "+61400000000", "DEPOSIT_REQUESTED", now, now).run();
  });

  describe("POST /api/threads/:threadId/deposits", () => {
    it("should create a deposit", async () => {
      const body = JSON.stringify({ amount: 150.0, currency: "AUD" });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(201);
      const data = await res.json();
      expect(data.data.amount).toBe(150);
      expect(data.data.currency).toBe("AUD");
      expect(data.data.status).toBe("PENDING");
      expect(data.data.threadId).toBe("dep-thread");
    });

    it("should default currency to AUD", async () => {
      const body = JSON.stringify({ amount: 200 });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(201);
      const data = await res.json();
      expect(data.data.currency).toBe("AUD");
    });

    it("should return 400 for missing amount", async () => {
      const body = JSON.stringify({});

      const res = await authFetch(
        "/api/threads/dep-thread/deposits",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });

    it("should return 404 for nonexistent thread", async () => {
      const body = JSON.stringify({ amount: 100 });

      const res = await authFetch(
        "/api/threads/nonexistent/deposits",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(404);
    });
  });

  describe("GET /api/threads/:threadId/deposits", () => {
    it("should list deposits for a thread", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("list-dep-1", "dep-thread", 100, "AUD", "PENDING", now).run();

      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("list-dep-2", "dep-thread", 200, "AUD", "VERIFIED", now).run();

      const res = await authFetch(
        "/api/threads/dep-thread/deposits",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data).toHaveLength(2);
    });
  });

  describe("GET /api/threads/:threadId/deposits/:depositId", () => {
    it("should return a specific deposit", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("get-dep", "dep-thread", 100, "AUD", "PENDING", now).run();

      const res = await authFetch(
        "/api/threads/dep-thread/deposits/get-dep",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.id).toBe("get-dep");
      expect(data.data.amount).toBe(100);
    });

    it("should return 404 for nonexistent deposit", async () => {
      const res = await authFetch(
        "/api/threads/dep-thread/deposits/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(404);
    });
  });

  describe("PATCH /api/threads/:threadId/deposits/:depositId", () => {
    it("should update deposit status to RECEIVED", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("update-dep", "dep-thread", 100, "AUD", "PENDING", now).run();

      const body = JSON.stringify({ status: "RECEIVED" });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits/update-dep",
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.status).toBe("RECEIVED");
    });

    it("should update deposit status to VERIFIED with evidence", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("verify-dep", "dep-thread", 100, "AUD", "RECEIVED", now).run();

      const body = JSON.stringify({
        status: "VERIFIED",
        evidenceType: "STRIPE_WEBHOOK",
        evidenceRef: "stripe_event_12345",
      });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits/verify-dep",
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.status).toBe("VERIFIED");
      expect(data.data.evidenceType).toBe("STRIPE_WEBHOOK");
      expect(data.data.evidenceRef).toBe("stripe_event_12345");
    });

    it("should require evidence for VERIFIED status", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("no-evidence-dep", "dep-thread", 100, "AUD", "RECEIVED", now).run();

      const body = JSON.stringify({ status: "VERIFIED" });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits/no-evidence-dep",
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body },
      );

      expect(res.status).toBe(400);
    });

    it("should update deposit status to FAILED", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("fail-dep", "dep-thread", 100, "AUD", "PENDING", now).run();

      const body = JSON.stringify({ status: "FAILED" });

      const res = await authFetch(
        "/api/threads/dep-thread/deposits/fail-dep",
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.status).toBe("FAILED");
    });
  });
});
