import { describe, it, expect } from "vitest";
import { readFileSync, existsSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(import.meta.dirname, "..");

function readMd(filename: string): string {
  const path = join(ROOT, filename);
  if (!existsSync(path)) return "";
  return readFileSync(path, "utf-8");
}

const adapters = [
  { file: "CLAUDE.md", name: "Claude Code" },
  { file: "CODEX.md", name: "Codex CLI" },
  { file: "GEMINI.md", name: "Gemini CLI" },
  { file: ".cursorrules", name: "Cursor" },
  { file: ".factory/AGENTS.md", name: "Factory Droid" },
];

describe("Harness adapters", () => {
  it("should have all adapter files", () => {
    for (const adapter of adapters) {
      const content = readMd(adapter.file);
      expect(content.length, `${adapter.file} should exist`).toBeGreaterThan(0);
    }
  });

  for (const adapter of adapters) {
    describe(`${adapter.name} (${adapter.file})`, () => {
      const content = readMd(adapter.file);

      it("should reference the master AGENTS.md spec", () => {
        expect(content).toContain("AGENTS.md");
      });

      it("should contain cognitive OS directives", () => {
        expect(content.toLowerCase()).toMatch(/cognitive|directive|spec|agent/i);
      });

      it("should have meaningful content (>200 chars)", () => {
        expect(content.length).toBeGreaterThan(200);
      });

      it("should be under 50KB", () => {
        const path = join(ROOT, adapter.file);
        if (existsSync(path)) {
          const size = statSync(path).size;
          expect(size).toBeLessThan(51200);
        }
      });
    });
  }
});
