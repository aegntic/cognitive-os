import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env } from "cloudflare:test";
import { setupTestDb, clearTestData } from "./helpers";

describe("D1 Schema", () => {
  beforeAll(async () => {
    await setupTestDb();
  });

  beforeEach(async () => {
    await clearTestData();
  });

  describe("Table existence", () => {
    it("should have threads table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='threads'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have messages table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='messages'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have personas table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='personas'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have deposits table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='deposits'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have audit_logs table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='audit_logs'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have device_keys table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='device_keys'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have thread_memory table", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='thread_memory'")
        .first();
      expect(result).not.toBeNull();
    });
  });

  describe("CHECK constraints - threads.state", () => {
    it("should accept valid thread states", async () => {
      const now = new Date().toISOString();
      const validStates = [
        "NEW", "GREETING", "CONVERSING", "DEPOSIT_REQUESTED",
        "DEPOSIT_PENDING", "HUMAN_REVIEW", "CONFIRMED",
        "ESCALATED", "ENDED", "STALLED", "AI_CHALLENGED", "COOLDOWN",
      ];

      for (const state of validStates) {
        await env.DB.prepare(
          "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind(`test-${state}`, "persona-1", "+1234567890", state, now, now).run();
      }

      const count = await env.DB
        .prepare("SELECT COUNT(*) as c FROM threads WHERE id LIKE 'test-%'")
        .first();
      expect(count?.c).toBe(12);
    });

    it("should reject invalid thread state", async () => {
      const now = new Date().toISOString();
      await expect(
        env.DB.prepare(
          "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind("test-invalid", "persona-1", "+1234567890", "INVALID_STATE", now, now).run(),
      ).rejects.toThrow();
    });
  });

  describe("CHECK constraints - deposits.status", () => {
    it("should accept valid deposit statuses", async () => {
      const now = new Date().toISOString();
      // Create a thread first
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("dep-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      const validStatuses = ["PENDING", "RECEIVED", "VERIFIED", "FAILED"];
      for (const status of validStatuses) {
        await env.DB.prepare(
          "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind(`dep-${status}`, "dep-thread", 100, "AUD", status, now).run();
      }

      const count = await env.DB
        .prepare("SELECT COUNT(*) as c FROM deposits WHERE id LIKE 'dep-%'")
        .first();
      expect(count?.c).toBe(4);
    });

    it("should reject invalid deposit status", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("dep-thread-2", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await expect(
        env.DB.prepare(
          "INSERT INTO deposits (id, thread_id, amount, currency, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind("dep-invalid", "dep-thread-2", 100, "AUD", "INVALID", now). run(),
      ).rejects.toThrow();
    });
  });

  describe("Defaults", () => {
    it("should default revision to 1", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("default-rev", "persona-1", "+1234567890", "NEW", now, now).run();

      const thread = await env.DB
        .prepare("SELECT revision FROM threads WHERE id = ?")
        .bind("default-rev")
        .first();
      expect(thread?.revision).toBe(1);
    });

    it("should default deposit currency to AUD", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("curr-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?)",
      ).bind("curr-dep", "curr-thread", 100, "PENDING", now).run();

      const dep = await env.DB
        .prepare("SELECT currency FROM deposits WHERE id = ?")
        .bind("curr-dep")
        .first();
      expect(dep?.currency).toBe("AUD");
    });

    it("should default deposit status to PENDING", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("status-dep-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, currency, created_at) VALUES (?, ?, ?, ?, ?)",
      ).bind("status-dep", "status-dep-thread", 100, "AUD", now).run();

      const dep = await env.DB
        .prepare("SELECT status FROM deposits WHERE id = ?")
        .bind("status-dep")
        .first();
      expect(dep?.status).toBe("PENDING");
    });
  });

  describe("FK cascade rules", () => {
    it("should cascade delete messages when thread is deleted", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("cascade-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await env.DB.prepare(
        "INSERT INTO messages (id, thread_id, direction, body, timestamp) VALUES (?, ?, ?, ?, ?)",
      ).bind("msg-1", "cascade-thread", "inbound", "hello", now).run();

      // Delete thread
      await env.DB.prepare("DELETE FROM threads WHERE id = ?")
        .bind("cascade-thread").run();

      // Message should be gone
      const msg = await env.DB
        .prepare("SELECT id FROM messages WHERE id = ?")
        .bind("msg-1")
        .first();
      expect(msg).toBeNull();
    });

    it("should cascade delete deposits when thread is deleted", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("cascade-dep-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await env.DB.prepare(
        "INSERT INTO deposits (id, thread_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?)",
      ).bind("dep-cascade", "cascade-dep-thread", 100, "PENDING", now).run();

      await env.DB.prepare("DELETE FROM threads WHERE id = ?")
        .bind("cascade-dep-thread").run();

      const dep = await env.DB
        .prepare("SELECT id FROM deposits WHERE id = ?")
        .bind("dep-cascade")
        .first();
      expect(dep).toBeNull();
    });

    it("should NOT cascade delete audit_logs when thread is deleted", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("audit-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await env.DB.prepare(
        "INSERT INTO audit_logs (id, thread_id, action, actor, timestamp) VALUES (?, ?, ?, ?, ?)",
      ).bind("audit-1", "audit-thread", "test_action", "system", now).run();

      await env.DB.prepare("DELETE FROM threads WHERE id = ?")
        .bind("audit-thread").run();

      const audit = await env.DB
        .prepare("SELECT id FROM audit_logs WHERE id = ?")
        .bind("audit-1")
        .first();
      expect(audit).not.toBeNull();
    });
  });

  describe("Indexes", () => {
    it("should have index on messages(thread_id, timestamp)", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_messages_thread'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have index on threads(state, updated_at)", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_threads_state'")
        .first();
      expect(result).not.toBeNull();
    });

    it("should have index on threads(client_phone, state)", async () => {
      const result = await env.DB
        .prepare("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_threads_phone'")
        .first();
      expect(result).not.toBeNull();
    });
  });

  describe("NOT NULL constraints", () => {
    it("should require persona_id for threads", async () => {
      const now = new Date().toISOString();
      await expect(
        env.DB.prepare(
          "INSERT INTO threads (id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        ).bind("null-persona", "+1234567890", "NEW", now, now).run(),
      ).rejects.toThrow();
    });

    it("should require client_phone for threads", async () => {
      const now = new Date().toISOString();
      await expect(
        env.DB.prepare(
          "INSERT INTO threads (id, persona_id, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        ).bind("null-phone", "persona-1", "NEW", now, now).run(),
      ).rejects.toThrow();
    });

    it("should require body for messages", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("msg-thread", "persona-1", "+1234567890", "CONVERSING", now, now).run();

      await expect(
        env.DB.prepare(
          "INSERT INTO messages (id, thread_id, direction, timestamp) VALUES (?, ?, ?, ?)",
        ).bind("msg-null", "msg-thread", "inbound", now).run(),
      ).rejects.toThrow();
    });
  });
});
