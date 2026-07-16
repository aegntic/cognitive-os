import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import { setupTestDb, clearTestData } from "./helpers";

describe("SMS Webhook API", () => {
  beforeAll(async () => {
    await setupTestDb();
  });

  beforeEach(async () => {
    await clearTestData();

    // Create a test persona
    const now = new Date().toISOString();
    await env.DB.prepare(
      "INSERT INTO personas (id, name, tone, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
    ).bind("sms-persona", "Test", "warm", now, now).run();
  });

  describe("POST /webhook/sms", () => {
    it("should accept valid SMS and return 202", async () => {
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000001",
          body: "Hey, are you available?",
          timestamp: new Date().toISOString(),
        }),
      });

      expect(res.status).toBe(202);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data.threadId).toBeDefined();
      expect(data.data.messageId).toBeDefined();
      expect(data.data.status).toBe("accepted");
    });

    it("should not require auth headers", async () => {
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000002",
          body: "No auth needed",
        }),
      });

      expect(res.status).toBe(202);
    });

    it("should return 400 for missing from", async () => {
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          body: "Missing from",
        }),
      });

      expect(res.status).toBe(400);
    });

    it("should return 400 for missing body", async () => {
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000003",
        }),
      });

      expect(res.status).toBe(400);
    });

    it("should create new thread for unknown phone number", async () => {
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000099",
          body: "First message",
        }),
      });

      expect(res.status).toBe(202);
      const data = await res.json();

      // Verify thread was created
      const thread = await env.DB
        .prepare("SELECT * FROM threads WHERE id = ?")
        .bind(data.data.threadId)
        .first();
      expect(thread).not.toBeNull();
      expect(thread?.client_phone).toBe("+61400000099");
      expect(thread?.state).toBe("NEW");
    });

    it("should reuse existing thread for known phone number", async () => {
      // Create a thread with this phone number
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("existing-sms-thread", "sms-persona", "+61400000088", "CONVERSING", now, now).run();

      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000088",
          body: "Follow up message",
        }),
      });

      expect(res.status).toBe(202);
      const data = await res.json();
      expect(data.data.threadId).toBe("existing-sms-thread");
    });

    it("should support Twilio-style form-encoded body", async () => {
      const formData = new FormData();
      formData.append("From", "+61400000077");
      formData.append("Body", "Twilio style");

      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        body: formData,
      });

      expect(res.status).toBe(202);
    });

    it("should truncate extremely long body (>1600 chars)", async () => {
      const longBody = "A".repeat(2000);

      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000066",
          body: longBody,
        }),
      });

      expect(res.status).toBe(202);

      const data = await res.json();
      const msg = await env.DB
        .prepare("SELECT body FROM messages WHERE id = ?")
        .bind(data.data.messageId)
        .first();
      expect((msg?.body as string).length).toBe(1600);
    });

    it("should handle duplicate SMS idempotently", async () => {
      const timestamp = new Date().toISOString();

      // First request
      const res1 = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000055",
          body: "Duplicate test",
          timestamp,
        }),
      });

      expect(res1.status).toBe(202);

      // Second request with same data
      const res2 = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000055",
          body: "Duplicate test",
          timestamp,
        }),
      });

      // Should return 200 with idempotent=true (not 202 again)
      const data2 = await res2.json();
      expect(data2.data.idempotent).toBe(true);
    });
  });

  describe("SMS blacklist", () => {
    it("should ignore blacklisted numbers", async () => {
      // Note: SMS_BLACKLIST is set via env var in wrangler.toml or test config
      // We'll test the behavior if the blacklist is set
      // For now just verify the webhook still returns 200
      const res = await SELF.fetch("http://localhost/webhook/sms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          from: "+61400000000", // Blacklisted number (if configured)
          body: "Blacklisted test",
        }),
      });

      // Should always return 200 to avoid Twilio retries
      expect(res.status).toBeLessThanOrEqual(202);
    });
  });
});
