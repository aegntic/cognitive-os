# Codex AGENTS.md — Cognitive OS v3.0 (OpenAI-Optimized)
> Full spec: /home/ae/AE/AGENTS.md

## Active Directives
1. Classify task complexity (T0-T4) before starting work
2. Re-read files before editing; context decays after compaction
3. Verify: typecheck → lint → test → build. No silent success claims.
4. Sub-agent swarming for tasks >5 files (group by semantic boundary)
5. Security scan before any commit (no secrets, validate inputs)
6. Phased execution: max 5 files per phase, verify between phases
7. Functions <50 lines, files <800 lines, immutability-first

## Merge CLI
- Search in parallel: `merge search-tools "<intent>" --connector X`
- Execute: `merge execute-tool <name> '<json>'`
- Never guess tool names. Never call APIs directly.

## Package Managers
bun.lock → bun | pnpm-lock.yaml → pnpm | package-lock.json → npm | uv.lock → uv
Never mix managers. Commit lockfile with manifest.

## GitNexus
Run impact analysis before editing symbols. Detect changes before commit.
