# Milestones

> Each milestone is a shippable increment with its own acceptance gates. Nothing ships without passing its gates. Card-contract friendly: each M-card below is a self-contained work unit with verifiable acceptance criteria (see the `card-contract-orchestration` workflow).

## M0 — Walking skeleton (cycle runs, gates fire) [~1 wk]

**Scope:** CLI `init` / `cycle` / `search` / `health` over one fixture vault; SQLite + FTS5 + vec hybrid search; local ollama embedder; the gate block; cycles ledger.

**Acceptance gates:**
1. `cos-engine init --vaults fixture/ --embedder ollama:nomic-embed-text` → brain created, config saved.
2. `cos-engine cycle --json` on clean fixture → status `green`, discovered == imported == embedded, exit 0.
3. Kill ollama → `cycle` → exit 1, ledger row `failed` naming the embed gate. **(the incident test)**
4. `cos-engine search "query" --json` → hits with `_meta.fused_from`, latency < 150 ms warm.
5. Second cycle, no changes → `skipped` counts, not zero-imports-read-as-green.
6. `cos-engine health log` → both cycles visible, statuses honest.

## M1 — Real vaults, watcher, export [~1 wk]

**Scope:** multi-vault sources, per-source isolation, mtime+hash delta cycles, inotify watcher + systemd user unit, Obsidian export (md graph index + dot).

**Acceptance gates:**
1. Import the four production vaults (3,652 notes) → green cycle, per-vault counts in ledger.
2. Touch one file → cycle imports 1, embeds only stale chunks, < 10 s.
3. systemctl user unit survives reboot; auto-restart on failure; `Restart=on-failure` honored.
4. Export dir renders; dot graph opens in Obsidian; no export loop-back into import (exclude `gbrain/`).
4b. Deleted vault file → page pruned on next cycle, ledger row records the reconcile.

## M1.5 — Dual-boot reality

**Scope:** brain path + config under `~/.cognitive-os/` (symlinked across OS), same behavior.

**Gates:** brain opens identically from either OS; WAL not corrupted by OS switch; documented in runbook.

> The SQLite brain is a single portable file — the dual-boot share pattern proven on this machine (symlink into `/mnt/ubuntu/home/ae`) applies directly.

## M2 — Episodic tier + MCP server [~1 wk]

**Scope:** `events` table, event ingest from cycles + manual `log-event`, Hermes MCP stdio server (`search`, `get_page`, `recent_events`, `health`).

**Gates:**
1. Cycle completion writes an episodic event; `recent_events` MCP tool returns it.
2. Hermes query round-trip: search via MCP returns same results as CLI.
3. MCP server crash → supervisor restarts; health shows the gap.

## M3 — Semantic fusion: entities + links [~1 wk]

**Scope:** entity extraction (headings, wiki-links, frontmatter refs), links table, `graph(query)` traversal, entity-level export.

**Gates:**
1. Fixture vault with known wiki-link topology → extracted graph matches expected edge count ±5%.
2. `graph("clawREFORM")` returns neighbors with edge kinds.
3. Export includes per-entity md + full dot graph.

## M4 — Procedural tier + decay [~1 wk]

**Scope:** skills table with §8.2 lifecycle stages, event-driven promotion/demotion, §3.4 decay sweep + reinforcement, `decay report`.

**Gates:**
1. Simulated 3 successes on a skill → stage advances observation→hypothesis→instinct, events recorded.
2. 45-day-old unreinforced memory → confidence ≈ e^(−45/30) ≈ 0.22 → archived, listed in `decay report`, still queryable with `--archived`.
3. Search hit → reinforcement event recorded; confidence floor lifted.

## M5 — Polish, docs, v0.1 release [~1 wk]

**Scope:** README quickstart, runbooks (dual-boot, ollama, systemd), failure-injection test suite green, comparison table verified against gbrain current, `cargo clippy --deny warnings`, tag `v0.1.0`.

**Gates:** fresh-machine quickstart works in ≤ 5 commands; all M0–M4 gates still pass; release tagged.

## Post-v0.1 (explicitly not promised)

Supabase/Postgres backend for multi-agent concurrent access; re-ranking; multi-brain federation; Dream-style background synthesis; eval harness (BrainBench-style) — spec'd in ARCHITECTURE.md, built only on demand.
