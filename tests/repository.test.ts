import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { join } from "node:path";
import { execSync } from "node:child_process";

const ROOT = join(import.meta.dirname, "..");

function getTrackedFiles(): string[] {
  try {
    const output = execSync("git ls-tree -r --name-only HEAD", {
      cwd: ROOT,
      encoding: "utf-8",
      timeout: 5000,
    });
    return output.trim().split("\n").filter(Boolean);
  } catch {
    return [];
  }
}

function getAllMarkdownFiles(): string[] {
  const tracked = getTrackedFiles();
  return tracked.filter((f) => f.endsWith(".md") || f.endsWith(".cursorrules"));
}

describe("Repository structure", () => {
  it("should have a README.md", () => {
    expect(existsSync(join(ROOT, "README.md"))).toBe(true);
  });

  it("should have a CONTRIBUTING.md", () => {
    expect(existsSync(join(ROOT, "CONTRIBUTING.md"))).toBe(true);
  });

  it("should have an .env.example", () => {
    expect(existsSync(join(ROOT, ".env.example"))).toBe(true);
  });

  it("should have CODEOWNERS", () => {
    expect(existsSync(join(ROOT, ".github", "CODEOWNERS"))).toBe(true);
  });

  it("should have a PR template", () => {
    expect(existsSync(join(ROOT, ".github", "pull_request_template.md"))).toBe(true);
  });

  it("should have issue templates", () => {
    expect(existsSync(join(ROOT, ".github", "ISSUE_TEMPLATE", "bug_report.md"))).toBe(true);
    expect(existsSync(join(ROOT, ".github", "ISSUE_TEMPLATE", "feature_request.md"))).toBe(true);
  });

  it("should have a CI workflow", () => {
    expect(existsSync(join(ROOT, ".github", "workflows", "ci.yml"))).toBe(true);
  });

  it("should have a release workflow", () => {
    expect(existsSync(join(ROOT, ".github", "workflows", "release.yml"))).toBe(true);
  });

  it("should have a devcontainer", () => {
    expect(existsSync(join(ROOT, ".devcontainer", "devcontainer.json"))).toBe(true);
  });

  it("should have a skills directory", () => {
    expect(existsSync(join(ROOT, ".factory", "skills", "repo-validation", "SKILL.md"))).toBe(true);
  });
});

describe("File size limits", () => {
  const mdFiles = getAllMarkdownFiles();

  for (const file of mdFiles) {
    it(`${file} should be under 50KB`, () => {
      const path = join(ROOT, file);
      if (existsSync(path)) {
        const stat = statSync(path);
        expect(stat.size).toBeLessThan(51200);
      }
    });
  }
});

describe("No hardcoded secrets", () => {
  const mdFiles = getAllMarkdownFiles();
  const secretPatterns = [
    /sk-[a-zA-Z0-9]{20,}/i,
    /ghp_[a-zA-Z0-9]{36}/i,
    /AKIA[A-Z0-9]{16}/i,
    /-----BEGIN [A-Z]+ PRIVATE KEY-----/i,
  ];

  for (const file of mdFiles) {
    it(`${file} should not contain secrets`, () => {
      const path = join(ROOT, file);
      if (!existsSync(path)) return;
      const content = readFileSync(path, "utf-8");
      for (const pattern of secretPatterns) {
        expect(content, `${file} should not match ${pattern}`).not.toMatch(pattern);
      }
    });
  }
});
