import { describe, it, expect } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(import.meta.dirname, "..");

describe("README.md", () => {
  const content = readFileSync(join(ROOT, "README.md"), "utf-8");

  it("should exist and be non-empty", () => {
    expect(content.length).toBeGreaterThan(100);
  });

  it("should have a title", () => {
    expect(content).toMatch(/^#\s+.+/m);
  });

  it("should document quick start instructions", () => {
    expect(content.toLowerCase()).toContain("git clone");
  });

  it("should document npm test command", () => {
    expect(content).toContain("npm test");
  });

  it("should document build/validate commands", () => {
    expect(content).toContain("npm run");
  });

  it("should document the file structure", () => {
    expect(content).toContain("AGENTS.md");
  });
});

describe("CONTRIBUTING.md", () => {
  const content = readFileSync(join(ROOT, "CONTRIBUTING.md"), "utf-8");

  it("should exist and be non-empty", () => {
    expect(content.length).toBeGreaterThan(100);
  });

  it("should document naming conventions", () => {
    expect(content.toLowerCase()).toContain("naming");
  });

  it("should document testing guidelines", () => {
    expect(content.toLowerCase()).toContain("test");
  });

  it("should document git workflow", () => {
    expect(content.toLowerCase()).toContain("commit");
  });
});

describe("Documentation freshness", () => {
  it("README should reference all harness adapters", () => {
    const content = readFileSync(join(ROOT, "README.md"), "utf-8");
    expect(content).toContain("CLAUDE.md");
    expect(content).toContain("CODEX.md");
    expect(content).toContain("GEMINI.md");
    expect(content).toContain(".cursorrules");
  });
});
