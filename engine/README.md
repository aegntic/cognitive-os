# cognitive-os-engine

> The executable memory tier of the [Cognitive OS](https://github.com/aegntic/cognitive-os) specification.
> Vault-canonical. Disk-backed. Loud by default.

## What this is

`cognitive-os` is a 699-line _specification_ — a cognitive architecture carried as prompt protocol by AI harnesses (Claude Code, Hermes, Codex, Gemini, Cursor). It defines a four-tier memory model (§3.1), a forgetting curve (§3.4), and a verification chain (§4.1). But a spec loaded into a context window stores nothing. This repo is the engine that turns §3 of the spec into running software.

**Status: M0 walking skeleton is runnable.** CLI `init` / `cycle` / `search` / `health` over a fixture vault, SQLite + FTS5 hybrid search, local ollama embeddings, cycle gates, cycles ledger. The design below is the committed roadmap; M0 is the first executable increment. The principles and architecture below describe the target design; only the M0 CLI capabilities are implemented. Four-tier tables, decay/reinforcement, MCP integration, export, and a rebuild command remain planned.

## Why this exists

The motivating incident is real and worth recording. A PGlite-backed brain (gbrain 0.18.2) grew to 7.1 GB, silently exceeded PGlite's in-memory WASM heap ceiling (~2–4 GB), and **every import failed for 2.5 months — 4,474 logged failures, zero successful imports, while the service reported "green" cycles** because unchanged-vault skip paths masked the dead database. Three failure classes, all structural:

1. **Ceiling** — an in-memory engine over a corpus that only grows is a time bomb.
2. **Silence** — a memory system whose writes fail quietly isn't a memory system.
3. **Opacity** — a derived index that must be treated as precious cargo inverts the source of truth.

The engine is designed against all three.

## Principles

1. **Vaults are canonical.** Markdown vaults (Obsidian or plain dirs) are the source of truth. The engine is a derived index that can be deleted and rebuilt from source at any time. `engine rebuild` is a first-class, boring operation.
2. **Disk-backed, no ceiling.** SQLite + [sqlite-vec](https://github.com/asg017/sqlite-vec). 64-bit addressing, memory-mapped reads, streaming writes. The corpus can outgrow any heap without the engine noticing.
3. **Loud by default.** Every cycle verifies its own work: imported-count vs discovered-count, embedded-count vs imported-count, export freshness. A failed gate fails the cycle, exits non-zero, and writes a JSONL health record. Silent no-op success is treated as a bug class of its own.
4. **Local-first.** Embeddings via local ollama (nomic-embed-text class) by default; zero API keys required for the core loop. Remote providers optional, pluggable.
5. **Four tiers, first-class.** Working (in-process), Episodic (events with timestamps and outcomes), Semantic (pages + entities + links), Procedural (skills with confidence). Not one flat table with a `type` column — separate access patterns, separate tables, one schema.
6. **Forgetting is a feature.** Every memory carries `confidence` decaying per spec §3.4 (`c = c0 · e^(−days/30)`); access reinforces. Below 0.3, memories archive — never delete (§8.2: "Never delete knowledge. Archive with timestamp and reason").
7. **Contradictions are kept, not resolved silently.** Newer wins for active decisions; both versions remain queryable with timestamps (§3.3).
8. **Cross-project isolation.** Instincts and indexes are scoped per project; no leakage between brains (§3.3).

## M0 — what you can run today

Run these commands from `engine/` (`cd engine` from the repository root).

```bash
cargo build
./target/debug/cos-engine --home /tmp/cos init --vaults fixtures/vault --embedder ollama:nomic-embed-text
./target/debug/cos-engine --home /tmp/cos cycle --json     # green: discovered == imported == embedded
./target/debug/cos-engine --home /tmp/cos search "silent failure" --json
./target/debug/cos-engine --home /tmp/cos health log
```

Verified 2026-08-27 against local ollama `nomic-embed-text`: first cycle 3/3/3 green; second cycle `skipped=3` (not a silent zero-import green); dead embedder URL fails at the **embed** gate, exit 1, ledger names the gate. Search returns `_meta.fused_from` from FTS5 + vector cosine.

Cycles commit page and chunk changes atomically. A failed cycle rolls back those changes and records a failed ledger entry; retries reprocess changed pages. Embeddings must contain the configured number of finite numeric values. Tests use explicit mock vectors and a loopback failure-injection server, not a live model.

Vectors live as BLOBs in SQLite (disk-backed, no WASM heap). sqlite-vec ANN is M1 if the corpus needs it — the ceiling that killed PGlite is already gone.

## Architecture, briefly

```
vaults (markdown, canonical)
   │  watch / cycle
   ▼
┌─────────────────────────────────────────────┐
│  engine core (Rust)                         │
│  parse → chunk → embed → fuse → gate        │
│  SQLite: pages, chunks, vec(FATS5+vec RRF)  │
│  episodic / semantic / procedural tables    │
└─────────────────────────────────────────────┘
   │                              │
   ▼                              ▼
MCP server (Hermes-first)    Obsidian export (gbrain/ dir)
```

Full detail: [ARCHITECTURE.md](ARCHITECTURE.md). Roadmap: [MILESTONES.md](MILESTONES.md).

## Comparison (honest, as of the incident)

| Capability              | gbrain 0.18 (incident)  | gbrain 0.46 (current)         | this engine (M0)                       |
| ----------------------- | ----------------------- | ----------------------------- | -------------------------------------- |
| Storage                 | PGlite, in-memory heap  | PGlite or Supabase Postgres   | SQLite + FTS5 + vector BLOBs (disk)    |
| Silent-failure defense  | none (the incident)     | partial (doctor improvements) | cycle gates, hard-fail, JSONL health   |
| Four tiers as schema    | no                      | partial                       | planned; M0 pages/chunks/cycles only   |
| Forgetting curve        | none                    | none                          | planned                                |
| Vault-canonical rebuild | no                      | no                            | derived index; rebuild command planned |
| Local embeddings        | fork patch              | native (ollama)               | native, default                        |
| Harness integration     | OpenClaw/Claude-centric | broad                         | CLI only; Hermes MCP planned           |

gbrain is excellent software with an active maintainer — the engine exists to execute _this_ spec's memory model faithfully, not to beat gbrain on features. Where gbrain is stronger, the table says so.

## License

MIT. See [LICENSE](LICENSE). The parent Cognitive OS specification has a separate
proprietary licence; neither component is relicensed by this consolidation.
