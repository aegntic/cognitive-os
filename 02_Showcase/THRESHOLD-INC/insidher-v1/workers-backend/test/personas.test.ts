import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import { env, SELF } from "cloudflare:test";
import {
  setupTestDb,
  clearTestData,
  generateTestKeyPair,
  authFetch,
} from "./helpers";

describe("Personas API", () => {
  let testKeys: { publicKey: string; privateKey: CryptoKey };
  const deviceId = "personas-test-device";

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

  describe("POST /api/personas", () => {
    it("should create a persona with valid data", async () => {
      const body = JSON.stringify({
        name: "Anita",
        tone: "warm, seductive",
        vocabulary: ["darling", "sweetheart", "love"],
        offerings: ["dinner date", "overnight"],
        depositWording: "A 30% deposit secures our time together.",
        boundaries: ["no explicit photos"],
        availabilityPolicy: {
          timezone: "Australia/Sydney",
          workingHours: { start: "10:00", end: "22:00" },
          workingDays: ["mon", "tue", "wed", "thu", "fri", "sat"],
        },
      });

      const res = await authFetch(
        "/api/personas",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(201);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data.name).toBe("Anita");
      expect(data.data.tone).toBe("warm, seductive");
      expect(data.data.vocabulary).toEqual(["darling", "sweetheart", "love"]);
    });

    it("should return 400 for missing name", async () => {
      const body = JSON.stringify({
        tone: "warm",
        availabilityPolicy: { timezone: "UTC" },
      });

      const res = await authFetch(
        "/api/personas",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });

    it("should return 400 for missing tone", async () => {
      const body = JSON.stringify({
        name: "Test",
        availabilityPolicy: { timezone: "UTC" },
      });

      const res = await authFetch(
        "/api/personas",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });

    it("should return 400 for missing availabilityPolicy", async () => {
      const body = JSON.stringify({
        name: "Test",
        tone: "warm",
      });

      const res = await authFetch(
        "/api/personas",
        testKeys.privateKey,
        deviceId,
        { method: "POST", body },
      );

      expect(res.status).toBe(400);
    });
  });

  describe("GET /api/personas", () => {
    it("should list all personas", async () => {
      // Create a persona first
      const createBody = JSON.stringify({
        name: "ListTest",
        tone: "warm",
        availabilityPolicy: { timezone: "UTC" },
      });
      await authFetch("/api/personas", testKeys.privateKey, deviceId, {
        method: "POST",
        body: createBody,
      });

      const res = await authFetch(
        "/api/personas",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.success).toBe(true);
      expect(data.data.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe("GET /api/personas/:personaId", () => {
    it("should return a persona by ID", async () => {
      const createBody = JSON.stringify({
        name: "GetTest",
        tone: "playful",
        availabilityPolicy: { timezone: "UTC" },
      });
      const createRes = await authFetch("/api/personas", testKeys.privateKey, deviceId, {
        method: "POST",
        body: createBody,
      });
      const created = await createRes.json();
      const personaId = created.data.id;

      const res = await authFetch(
        `/api/personas/${personaId}`,
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.name).toBe("GetTest");
    });

    it("should return 404 for nonexistent persona", async () => {
      const res = await authFetch(
        "/api/personas/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "GET" },
      );

      expect(res.status).toBe(404);
    });
  });

  describe("PATCH /api/personas/:personaId", () => {
    it("should update persona fields", async () => {
      const createBody = JSON.stringify({
        name: "UpdateTest",
        tone: "warm",
        availabilityPolicy: { timezone: "UTC" },
      });
      const createRes = await authFetch("/api/personas", testKeys.privateKey, deviceId, {
        method: "POST",
        body: createBody,
      });
      const created = await createRes.json();
      const personaId = created.data.id;

      const updateBody = JSON.stringify({
        name: "UpdatedName",
        tone: "sultry",
      });

      const res = await authFetch(
        `/api/personas/${personaId}`,
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body: updateBody },
      );

      expect(res.status).toBe(200);
      const data = await res.json();
      expect(data.data.name).toBe("UpdatedName");
      expect(data.data.tone).toBe("sultry");
    });

    it("should return 404 for updating nonexistent persona", async () => {
      const body = JSON.stringify({ name: "New" });

      const res = await authFetch(
        "/api/personas/nonexistent",
        testKeys.privateKey,
        deviceId,
        { method: "PATCH", body },
      );

      expect(res.status).toBe(404);
    });
  });
});
