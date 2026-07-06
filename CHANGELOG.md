# Changelog

All notable changes to Cognitive OS are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- CodeQL workflow for automated security analysis (JavaScript/TypeScript)
- Stale issue/PR bot (marks inactive items after 30 days, closes after 7 more)
- Build performance tracking in CI (times full validation, warns if >30s)
- Flaky test detection CI job (runs test suite 3x with shuffle)
- LICENSE file (proprietary, all rights reserved)
- SECURITY.md with vulnerability reporting and security measures documentation
- CHANGELOG.md (this file)
- Cross-references test suite (26 tests covering adapter cross-references, AGENTS.md internal consistency, governance file validation)
- CI/CodeQL/License/Tests badges in README
- Changelog and Security sections in README
- GitHub repository topics (ai-agents, cognitive-os, specification, llm, prompt-engineering, coding-agent, multi-harness)

### Changed

- Vitest config: added `retry: 1` and `sequence.shuffle: false` for flaky test prevention
- Dependabot upgraded lint-staged from 15.5.2 to 17.0.8 (PR #1)
- Test count increased from 80 to 106
- Markdownlint-cli2 upgraded from 0.18.1 to 0.23.0
- Vitest upgraded to 4.1.10

### Security

- CodeQL code scanning enabled (runs on push, PR, and weekly schedule)
- Stale bot exempts security-labeled issues and PRs

## [3.0.0] - 2026-06-25

### Added

- 16-section cognitive architecture (10-layer cognitive stack, multi-model orchestration, 4-layer memory, verification system, swarm coordination, adversarial security, economic intelligence, self-improvement pipeline)
- 5 harness adapters: Factory Droid, Claude Code, Cursor, Codex CLI, Gemini CLI
- CI/CD pipeline (lint, format check, tests, link check, tech debt scan, file size check, docs validation)
- Automated release workflow on spec changes
- 80 vitest tests across 4 test files
- Devcontainer configuration for VS Code
- Architecture diagrams (Mermaid) and runbooks
- CODEOWNERS, PR template, issue templates, dependabot
- Pre-commit hooks (husky + lint-staged + markdownlint + prettier)
- Branch protection, secret scanning, push protection
- Security policy and CONTRIBUTING guide

### Changed

- N/A (initial release)

### Deprecated

- N/A

### Removed

- N/A

### Fixed

- N/A

### Security

- Secret scanning with push protection enabled
- Branch protection enforced (no force push, no deletion, enforce admins)
- Pre-commit hooks prevent secrets in staged files
