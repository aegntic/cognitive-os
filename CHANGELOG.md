# Changelog

All notable changes to Cognitive OS are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
