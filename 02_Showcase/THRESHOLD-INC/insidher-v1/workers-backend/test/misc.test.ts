import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import {
  setupTestDb,
  clearTestData,
  generateTestKeyPair,
  authFetch,
} from "./helpers";

describe("Polling API & Health & CORS", () => {
  let testKeys: { publicKey: string; privateKey: CryptoKey };
  const deviceId = "polling-test-device";

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
  });

  describe("Health check", () => {
    it("GET /health should return 200", async () => {
      const res = await SELF.fetch("http://localhost/health");
      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data.status).toBe("healthy");
      expect(data.data.timestamp).toBeDefined();
    });

    it("GET /health should not require auth", async () => {
      const res = await SELF.fetch("http://localhost/health");
      expect(res.status).toBe(200);
    });
  });

  describe("CORS headers", () => {
    it("should include Access-Control-Allow-Origin in responses", async () => {
      const res = await SELF.fetch("http://localhost/health", {
        headers: { Origin: "https://insidher.app" },
      });
      expect(res.headers.get("Access-Control-Allow-Origin")).toBeDefined();
    });

    it("should handle OPTIONS preflight requests", async () => {
      const res = await SELF.fetch("http://localhost/api/threads", {
        method: "OPTIONS",
        headers: {
          Origin: "https://insidher.app",
          "Access-Control-Request-Method": "GET",
          "Access-Control-Request-Headers": "Content-Type, X-Device-Id",
        },
      });
      expect(res.status).toBe(204);
    });

    it("should allow configured methods", async () => {
      const res = await SELF.fetch("http://localhost/api/threads", {
        method: "OPTIONS",
        headers: {
          Origin: "https://insidher.app",
          "Access-Control-Request-Method": "POST",
        },
      });
      const allowMethods = res.headers.get("Access-Control-Allow-Methods");
      expect(allowMethods).toContain("GET");
      expect(allowMethods).toContain("POST");
      expect(allowMethods).toContain("PATCH");
    });
  });

  describe("GET /api/devices/:deviceId/outbound", () => {
    it("should return pending outbound SMS in FIFO order", async () => {
      const now = new Date().toISOString();
      const threadNow = new Date().toISOString();

      // Create thread
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("poll-thread", "poll-persona", "+61400000000", "CONVERSING", threadNow, threadNow).run();

      // Create outbound SMS in order
      for (let i = 0; i < 3; i++) {
        await env.DB.prepare(
          `INSERT INTO outbound_sms (id, thread_id, message_id, device_id, body, phone_number, scheduled_for, enqueued_at, sequence)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        ).bind(
          `sms-poll-${i}`,
          "poll-thread",
          `msg-poll-${i}`,
          deviceId,
          `Message ${i}`,
          "+61400000000",
          now,
          new Date(Date.now() + i * 1000).toISOString(),
          i,
        ).run();
      }

      const res = await authFetch(
        `/api/devices/${deviceId}/outbound`,
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.length).toBe(3);
      // Should be in FIFO order (sequence 0, 1, 2)
      expect(data.data[0].sequence).toBe(0);
      expect(data.data[1].sequence).toBe(1);
      expect(data.data[2].sequence).toBe(2);
    });

    it("should only return undelivered SMS", async () => {
      const now = new Date().toISOString();
      const threadNow = new Date().toISOString();

      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("delivered-thread", "poll-persona", "+61400000000", "CONVERSING", threadNow, threadNow).run();

      // Create one pending
      await env.DB.prepare(
        `INSERT INTO outbound_sms (id, thread_id, message_id, device_id, body, phone_number, scheduled_for, enqueued_at, sequence)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind("pending-sms", "delivered-thread", "msg-1", deviceId, "Pending", "+61400000000", now, now, 0).run();

      // Create one delivered
      await env.DB.prepare(
        `INSERT INTO outbound_sms (id, thread_id, message_id, device_id, body, phone_number, scheduled_for, enqueued_at, sequence, delivered, delivered_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind("delivered-sms", "delivered-thread", "msg-2", deviceId, "Delivered", "+61400000000", now, now, 1, 1, now).run();

      const res = await authFetch(
        `/api/devices/${deviceId}/outbound`,
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      const data = await res.json();
      // Should only return the pending one
      expect(data.data.length).toBe(1);
      expect(data.data[0].id).toBe("pending-sms");
    });
  });

  describe("POST /api/devices/:deviceId/outbound/:smsId/delivered", () => {
    it("should mark SMS as delivered", async () => {
      const now = new Date().toISOString();
      const threadNow = new Date().toISOString();

      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("mark-thread", "poll-persona", "+61400000000", "CONVERSING", threadNow, threadNow).run();

      await env.DB.prepare(
        `INSERT INTO outbound_sms (id, thread_id, message_id, device_id, body, phone_number, scheduled_for, enqueued_at, sequence)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind("mark-sms", "mark-thread", "msg-mark", deviceId, "To mark", "+61400000000", now, now, 0).run();

      const res = await authFetch(
        `/api/devices/${deviceId}/outbound/mark-sms/delivered`,
        testKeys.privateKey,
        deviceId,
        { method: "POST" },
      );

      expect(res.status).toBe(200);

      // Verify in DB
      const sms = await env.DB
        .prepare("SELECT delivered FROM outbound_sms WHERE id = ?")
        .bind("mark-sms")
        .first();
      expect(sms?.delivered).toBe(1);
    });
  });

  describe("Error response envelope", () => {
    it("should have consistent error format", async () => {
      const res = await authFetch(
        "/api/threads/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      const data = await res.json();
      expect(data).toHaveProperty("success", false);
      expect(data).toHaveProperty("error");
      expect(data.error).toHaveProperty("code");
      expect(data.error).toHaveProperty("message");
    });
  });

  describe("API response envelope", () => {
    it("should have consistent success format", async () => {
      // Create persona and thread
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO personas (id, name, tone, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
      ).bind("env-persona", "Test", "warm", now, now).run();

      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("env-thread", "env-persona", "+61400000000", "CONVERSING", now, now).run();

      const res = await authFetch(
        "/api/threads/env-thread",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      const data = await res.json();
      expect(data).toHaveProperty("success", true);
      expect(data).toHaveProperty("data");
      expect(data.data).toHaveProperty("id");
    });
  });
});
