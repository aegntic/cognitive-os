import { describe, it, expect } from "vitest";
import { readFileSync, existsSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(import.meta.dirname, "..");

function readMd(filename: string): string {
  const path = join(ROOT, filename);
  if (!existsSync(path)) return "";
  return readFileSync(path, "utf-8");
}

describe("AGENTS.md master specification", () => {
  const content = readMd("AGENTS.md");

  it("should exist", () => {
    expect(content.length).toBeGreaterThan(0);
  });

  it("should have at least 100 lines", () => {
    const lines = content.split("\n").length;
    expect(lines).toBeGreaterThan(100);
  });

  it("should contain the cognitive architecture section", () => {
    expect(content).toContain("COGNITIVE ARCHITECTURE");
  });

  it("should contain all 10 layers", () => {
    for (let i = 1; i <= 10; i++) {
      expect(content).toContain(`Layer ${i}:`);
    }
  });

  it("should contain the memory stack section", () => {
    expect(content).toContain("MEMORY STACK");
  });

  it("should contain the verification system", () => {
    expect(content).toContain("VERIFICATION SYSTEM");
  });

  it("should contain the security framework", () => {
    expect(content).toContain("SECURITY FRAMEWORK");
  });

  it("should contain the economic intelligence section", () => {
    expect(content).toContain("ECONOMIC INTELLIGENCE");
  });

  it("should contain the self-improvement pipeline", () => {
    expect(content).toContain("SELF-IMPROVEMENT");
  });

  it("should contain the swarm coordination protocol", () => {
    expect(content).toContain("SWARM COORDINATION");
  });
});
