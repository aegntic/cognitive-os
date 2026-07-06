# Cognitive OS v3.0

[![CI](https://github.com/aegntic/cognitive-os/actions/workflows/ci.yml/badge.svg)](https://github.com/aegntic/cognitive-os/actions/workflows/ci.yml)
[![CodeQL](https://github.com/aegntic/cognitive-os/actions/workflows/codeql.yml/badge.svg)](https://github.com/aegntic/cognitive-os/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Proprietary-red)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-80%20passing-brightgreen)](https://github.com/aegntic/cognitive-os/actions/workflows/ci.yml)

> The future is not predicted. It is compiled.

A universal, multi-harness cognitive operating system specification for AI coding agents. This repository defines a 16-section cognitive architecture that governs how AI agents think, plan, verify, and execute across real-world systems.

## What This Is

Cognitive OS is not an application or library. It is a **living specification** that defines:

1. A 10-layer cognitive stack (intake, reasoning, context budgeting, diff-awareness, tool blindness prevention, semantic search, generated file awareness, phased execution, rollback planning, dependency discipline)
2. Multi-model orchestration protocols (task complexity routing, ensemble verification, cost-aware execution)
3. A 4-layer agentic memory stack (working, episodic, semantic, procedural)
4. Autonomous verification systems (verification chain, self-healing pipeline, adversarial self-red-teaming)
5. Swarm coordination protocol (sub-agent swarming, worktree isolation, DAG execution)
6. Adversarial security framework (pre-commit checklist, prompt injection defense, circuit breakers)
7. Economic intelligence (token economics, ROI-aware routing, context window as scarce resource)
8. Self-improvement pipeline (continuous learning loop, instinct evolution, failure as signal)

## Harness Adapters

The spec auto-adapts to different AI agent harnesses:

| Harness       | Config File          | Purpose                                                |
| ------------- | -------------------- | ------------------------------------------------------ |
| Factory Droid | `.factory/AGENTS.md` | Droid-optimized shims, MCP servers, sub-agent strategy |
| Claude Code   | `CLAUDE.md`          | ECC agent registry, gstack integration, model routing  |
| Cursor        | `.cursorrules`       | Compact subset for code completion and chat            |
| Codex CLI     | `CODEX.md`           | OpenAI-optimized directives                            |
| Gemini CLI    | `GEMINI.md`          | Google-optimized with Gemini extensions                |

## Quick Start

```bash
# Clone
git clone https://github.com/aegntic/cognitive-os.git
cd cognitive-os

# Install tooling (markdown linter, formatter, pre-commit hooks)
npm install

# Validate all markdown files
npm test

# Run development checks (lint + format + test)
npm run dev

# Start editing AGENTS.md or any harness adapter file
```

## Build & Test Commands

| Command                | Description                                            |
| ---------------------- | ------------------------------------------------------ |
| `npm install`          | Install dependencies and set up pre-commit hooks       |
| `npm test`             | Run markdown validation tests                          |
| `npm run lint`         | Lint all markdown files with markdownlint              |
| `npm run lint:fix`     | Auto-fix linting issues                                |
| `npm run format`       | Format all files with Prettier                         |
| `npm run format:check` | Check formatting without modifying files               |
| `npm run validate`     | Run full validation suite (lint + format check + test) |

## File Structure

```
.
├── AGENTS.md              # Master specification (699 lines, 16 sections)
├── CLAUDE.md              # Claude Code adapter
├── CODEX.md               # Codex CLI adapter
├── GEMINI.md              # Gemini CLI adapter
├── .cursorrules           # Cursor adapter
├── .factory/
│   ├── AGENTS.md          # Factory Droid adapter
│   └── skills/            # Agent skills directory
├── .github/               # CI/CD, templates, governance
├── docs/                  # Architecture diagrams, runbooks
├── package.json           # Project tooling and scripts
└── README.md              # This file
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for naming conventions, testing guidelines, and development workflow.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and notable changes.

## Security

See [.github/SECURITY.md](.github/SECURITY.md) for vulnerability reporting and security measures.

## License

Proprietary. All rights reserved. See [LICENSE](LICENSE) for details.
