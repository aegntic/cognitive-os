import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import {
  setupTestDb,
  clearTestData,
  generateTestKeyPair,
  createAuthHeaders,
  authFetch,
} from "./helpers";

describe("Threads API", () => {
  let testKeys: { publicKey: string; privateKey: CryptoKey };
  const deviceId = "threads-test-device";

  beforeAll(async () => {
    await setupTestDb();
    testKeys = await generateTestKeyPair();

    // Register device
    await SELF.fetch("http://localhost/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        deviceId,
        publicKey: testKeys.publicKey,
      }),
    });

    // Create a test persona
    const now = new Date().toISOString();
    await env.DB.prepare(
      "INSERT INTO personas (id, name, tone, vocabulary, offerings, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(
      "test-persona",
      "Test Persona",
      "warm",
      JSON.stringify(["darling", "sweetheart"]),
      JSON.stringify(["dinner", "event"]),
      now,
      now,
    ).run();
  });

  beforeEach(async () => {
    // Clear only threads and messages, keep persona
    await env.DB.prepare("DELETE FROM outbound_sms").run();
    await env.DB.prepare("DELETE FROM messages").run();
    await env.DB.prepare("DELETE FROM deposits").run();
    await env.DB.prepare("DELETE FROM audit_logs").run();
    await env.DB.prepare("DELETE FROM threads").run();
  });

  describe("POST /api/threads", () => {
    it("should create a thread with valid data", async () => {
      const body = JSON.stringify({
        personaId: "test-persona",
        clientPhone: "+61400000000",
      });

      const res = await authFetch(
        "/api/threads",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(201);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data.id).toBeDefined();
      expect(data.data.state).toBe("NEW");
      expect(data.data.revision).toBe(1);
      expect(data.data.personaId).toBe("test-persona");
      expect(data.data.clientPhone).toBe("+61400000000");
    });

    it("should return 400 for missing personaId", async () => {
      const body = JSON.stringify({ clientPhone: "+61400000000" });

      const res = await authFetch(
        "/api/threads",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });

    it("should return 400 for missing clientPhone", async () => {
      const body = JSON.stringify({ personaId: "test-persona" });

      const res = await authFetch(
        "/api/threads",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });

    it("should return 400 for invalid persona reference", async () => {
      const body = JSON.stringify({
        personaId: "nonexistent-persona",
        clientPhone: "+61400000000",
      });

      const res = await authFetch(
        "/api/threads",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });
  });

  describe("GET /api/threads", () => {
    it("should list threads with pagination", async () => {
      // Create multiple threads
      const now = new Date().toISOString();
      for (let i = 0; i < 5; i++) {
        await env.DB.prepare(
          "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind(`list-thread-${i}`, "test-persona", `+61400000${i.toString().padStart(3, "0")}`, "CONVERSING", now, now).run();
      }

      const res = await authFetch(
        "/api/threads?page=1&pageSize=3",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data).toHaveLength(3);
      expect(data.pagination.total).toBe(5);
      expect(data.pagination.hasMore).toBe(true);
    });

    it("should filter by state", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("filter-new", "test-persona", "+61400000001", "NEW", now, now).run();

      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("filter-conversing", "test-persona", "+61400000002", "CONVERSING", now, now).run();

      const res = await authFetch(
        "/api/threads?state=NEW",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.every((t: { state: string }) => t.state === "NEW")).toBe(true);
    });
  });

  describe("GET /api/threads/:threadId", () => {
    it("should return a thread by ID", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("get-thread", "test-persona", "+61400000000", "CONVERSING", now, now).run();

      const res = await authFetch(
        "/api/threads/get-thread",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.id).toBe("get-thread");
    });

    it("should return 404 for nonexistent thread", async () => {
      const res = await authFetch(
        "/api/threads/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(404);
    });
  });

  describe("GET /api/threads/:threadId/state", () => {
    it("should return thread state and revision", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("state-thread", "test-persona", "+61400000000", "CONVERSING", 3, now, now).run();

      const res = await authFetch(
        "/api/threads/state-thread/state",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("CONVERSING");
      expect(data.data.revision).toBe(3);
    });
  });

  describe("POST /api/threads/:threadId/transition (CAS)", () => {
    it("should transition state with correct revision", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("cas-thread", "test-persona", "+61400000000", "NEW", 1, now, now).run();

      const body = JSON.stringify({
        newState: "GREETING",
        expectedRevision: 1,
        actor: "test",
      });

      const res = await authFetch(
        "/api/threads/cas-thread/transition",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("GREETING");
      expect(data.data.revision).toBe(2);
    });

    it("should return 409 on revision mismatch (CAS conflict)", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("cas-conflict", "test-persona", "+61400000000", "NEW", 1, now, now).run();

      const body = JSON.stringify({
        newState: "GREETING",
        expectedRevision: 99, // Wrong revision
        actor: "test",
      });

      const res = await authFetch(
        "/api/threads/cas-conflict/transition",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(409);
    });

    it("should reject invalid state transition", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("invalid-trans", "test-persona", "+61400000000", "ENDED", 1, now, now).run();

      const body = JSON.stringify({
        newState: "CONVERSING",
        expectedRevision: 1,
        actor: "test",
      });

      const res = await authFetch(
        "/api/threads/invalid-trans/transition",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(409);
    });

    it("should create audit log on transition", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("audit-trans", "test-persona", "+61400000000", "NEW", 1, now, now).run();

      const body = JSON.stringify({
        newState: "GREETING",
        expectedRevision: 1,
        actor: "test-actor",
      });

      await authFetch(
        "/api/threads/audit-trans/transition",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      const audit = await env.DB
        .prepare("SELECT * FROM audit_logs WHERE thread_id = ? AND action = ?")
        .bind("audit-trans", "state_transition")
        .first();
      expect(audit).not.toBeNull();
    });
  });

  describe("POST /api/threads/:threadId/end", () => {
    it("should end an active thread", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("end-thread", "test-persona", "+61400000000", "CONVERSING", 1, now, now).run();

      const res = await authFetch(
        "/api/threads/end-thread/end",
        testKeys.privateKey,
        deviceId,
        { method: "POST" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("ENDED");
    });
  });

  describe("POST /api/threads/:threadId/messages", () => {
    it("should accept a message and enqueue LLM processing", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("msg-thread", "test-persona", "+61400000000", "CONVERSING", 1, now, now).run();

      const body = JSON.stringify({
        body: "Hey, are you available tonight?",
      });

      const res = await authFetch(
        "/api/threads/msg-thread/messages",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(202);
      const data = await res.json();
      expect(data.data.direction).toBe("inbound");
      expect(data.data.body).toBe("Hey, are you available tonight?");

      // Verify message persisted
      const msg = await env.DB
        .prepare("SELECT * FROM messages WHERE thread_id = ?")
        .bind("msg-thread")
        .first();
      expect(msg).not.toBeNull();
    });

    it("should reject message for ENDED thread", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("ended-msg-thread", "test-persona", "+61400000000", "ENDED", 1, now, now).run();

      const body = JSON.stringify({ body: "hello" });

      const res = await authFetch(
        "/api/threads/ended-msg-thread/messages",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(409);
    });

    it("should reject empty message body", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("empty-msg-thread", "test-persona", "+61400000000", "CONVERSING", 1, now, now).run();

      const body = JSON.stringify({ body: "" });

      const res = await authFetch(
        "/api/threads/empty-msg-thread/messages",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });
  });

  describe("GET /api/threads/:threadId/messages", () => {
    it("should list messages with pagination", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind("list-msg-thread", "test-persona", "+61400000000", "CONVERSING", now, now).run();

      for (let i = 0; i < 10; i++) {
        await env.DB.prepare(
          "INSERT INTO messages (id, thread_id, direction, body, timestamp) VALUES (?, ?, ?, ?, ?)",
        ).bind(`list-msg-${i}`, "list-msg-thread", i % 2 === 0 ? "inbound" : "outbound", `Message ${i}`, new Date(Date.now() + i).toISOString()).run();
      }

      const res = await authFetch(
        "/api/threads/list-msg-thread/messages?page=1&pageSize=5",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data).toHaveLength(5);
      expect(data.pagination.total).toBe(10);
    });
  });

  describe("POST /api/threads/:threadId/decision", () => {
    it("should reject decision for non-HUMAN_REVIEW thread", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("dec-thread", "test-persona", "+61400000000", "CONVERSING", 1, now, now).run();

      const body = JSON.stringify({ decision: "APPROVE" });

      const res = await authFetch(
        "/api/threads/dec-thread/decision",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(409);
    });

    it("should approve thread from HUMAN_REVIEW to CONFIRMED", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("approve-thread", "test-persona", "+61400000000", "HUMAN_REVIEW", 1, now, now).run();

      const body = JSON.stringify({ decision: "APPROVE" });

      const res = await authFetch(
        "/api/threads/approve-thread/decision",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("CONFIRMED");
    });

    it("should reject thread from HUMAN_REVIEW to ENDED", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("reject-thread", "test-persona", "+61400000000", "HUMAN_REVIEW", 1, now, now).run();

      const body = JSON.stringify({ decision: "REJECT" });

      const res = await authFetch(
        "/api/threads/reject-thread/decision",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("ENDED");
    });

    it("should escalate thread from HUMAN_REVIEW to ESCALATED", async () => {
      const now = new Date().toISOString();
      await env.DB.prepare(
        "INSERT INTO threads (id, persona_id, client_phone, state, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      ).bind("escalate-thread", "test-persona", "+61400000000", "HUMAN_REVIEW", 1, now, now).run();

      const body = JSON.stringify({ decision: "ESCALATE" });

      const res = await authFetch(
        "/api/threads/escalate-thread/decision",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.state).toBe("ESCALATED");
    });
  });

  describe("Error responses", () => {
    it("should return proper error envelope on 404", async () => {
      const res = await authFetch(
        "/api/threads/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(404);
      const data = await res.json();
      expect(data.success).toBe(false);
      expect(data.error).toBeDefined();
      expect(data.error.code).toBeDefined();
      expect(data.error.message).toBeDefined();
    });

    it("should return 404 for unknown routes", async () => {
      const res = await authFetch(
        "/api/unknown-endpoint",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(404);
    });
  });
});
