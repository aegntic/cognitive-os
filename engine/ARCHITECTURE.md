# Architecture

> Direct implementation mapping of [Cognitive OS](https://github.com/aegntic/cognitive-os) §3 (Agentic Memory Stack), §4 (Autonomous Verification), §8.2 (Instinct Lifecycle), §12.1 (SPARC), §11 standards.
> Status: v0 design document. Sections marked **[v0.1]** ship in the first runnable build; the rest are the committed roadmap.

## 1. The three planes

```
┌──────────────────────────── SOURCE PLANE (canonical, never written by engine) ─┐
│  ObsidianVault/   clawreform/   archive/   sleepmoney/   … (any markdown dirs) │
└──────────────┬─────────────────────────────────────────────────────────────────┘
               │ watch (inotify) + cycle (full reconciliation)
               ▼
┌──────────────────────── ENGINE PLANE (derived, rebuildable) ───────────────────┐
│  ingest: parse → frontmatter strip → chunk (semantic, 400–800 tokens)         │
│  embed:  local ollama (default) → vectors → sqlite-vec                         │
│  fuse:   entities + links (wiki-links, tags, backlinks) → knowledge graph      │
│  gate:   imported==discovered ∧ embedded==imported ∨ justified ∨ CYCLE FAILS  │
│  tiers:  episodic | semantic | procedural — separate tables, one schema        │
│  decay:  confidence(t) per §3.4, reinforcement on access, archive < 0.3        │
└──────────────┬─────────────────────────────────────────────────────────────────┘
               │ MCP (stdio, Hermes-first)          │ export
               ▼                                    ▓
┌──────────────────────────── ACCESS PLANE ──────────────────────────────────────┐
│  Hermes (primary) · Claude Code · Codex · any MCP client                        │
│  ObsidianVault/gbrain/ (graph export, md+dot) — human-browsable                │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Storage — SQLite, disk-backed [v0.1]

One file per brain: `~/.cognitive-os/<name>/brain.sqlite` (+ `-wal`, `-shm`).

```sql
-- semantic tier [v0.1]
CREATE TABLE pages   (id INTEGER PK, path TEXT UNIQUE, source_id TEXT, title TEXT,
                      mtime INT, hash TEXT, confidence REAL DEFAULT 1.0,
                      archived INT DEFAULT 0, first_seen INT, last_access INT);
CREATE TABLE chunks  (id INTEGER PK, page_id INT REFERENCES pages, ord INT,
                      content TEXT, hash TEXT);
CREATE VIRTUAL TABLE vec USING vec0(chunks(id embedding float[768]));
CREATE TABLE entities(id INTEGER PK, name TEXT, kind TEXT);          -- [v0.2]
CREATE TABLE links   (src INT, dst INT, kind TEXT);                  -- [v0.2]

-- episodic tier [v0.3]: what happened, when, outcomes
CREATE TABLE events  (id INTEGER PK, ts INT, kind TEXT, subject TEXT,
                      summary TEXT, outcome TEXT, refs_json TEXT, confidence REAL);

-- procedural tier [v0.3]: skills with lifecycle
CREATE TABLE skills  (id INTEGER PK, name TEXT UNIQUE, body_ref TEXT,
                      stage TEXT CHECK(stage IN
                        ('observation','hypothesis','instinct','skill','automation','archived')),
                      successes INT DEFAULT 0, failures INT DEFAULT 0,
                      confidence REAL DEFAULT 0.5, last_reinforced INT);

-- verification records: the anti-silence ledger [v0.1]
CREATE TABLE cycles  (id INTEGER PK, started INT, ended INT, vault TEXT,
                      discovered INT, imported INT, embedded INT, exported INT,
                      status TEXT CHECK(status IN ('green','failed')),
                      detail_json TEXT);
```

**Why SQLite over PGlite/Postgres:** no heap ceiling (the 7.1 GB incident), no server, single-writer WAL is sufficient (cycles are serialized), `vec0` virtual tables give ANN search with plain SQL joins, and FTS5 + vec reciprocal-rank fusion is a proven hybrid pattern. Postgres is a v1.0+ scale-out option, not a requirement.

**Hybrid search [v0.1]:** `RRF(k=60)` of FTS5 MATCH and vec KNN (`k = 8·top_k`), filterable by tier, source, archived, confidence floor.

## 3. Cycle & verification gates (§4.1) [v0.1]

Every cycle (watch tick or manual) ends at the **gate block**. Any failed gate fails the cycle:

| Gate | Check | Justified-fail path |
|---|---|---|
| discovered | md files found ≥ 1 per existing vault | missing vault → warn, not fail |
| import | imported == discovered − explicit `skips` | parse errors → fail |
| embed | embedded == imported − explicit `skips` | embedder down → fail loud |
| export | export dir mtime > cycle start | export disabled → skip, recorded |
| reconcile | pages in DB ⊆ pages on disk (deleted files pruned) | quarantine dir → recorded |

`cos-engine cycle --json` prints the counts; a non-zero exit on any failed gate is the contract for systemd/supervisord. Health history: `cos-engine health last` / `cos-engine health log`. The 4,474-silent-failures incident is the canonical test case: with these gates, cycle one fails, exit 1, `cycles` row says why.

##  verification chain mapping (§4.1)

| Spec | Engine |
|---|---|
| type_check / lint / unit_tests | `cargo build && cargo clippy && cargo test` per PR |
| verification chain per feature | gate block + `cycles` ledger + test cycle on fixture vaults |
| adversarial red-team (§4.4) | failure-injection tests: dead embedder, missing vault, corrupted chunk, half-written export |

## 4. Decay & reinforcement (§3.4) [v0.4]

```
confidence = initial · exp(−days_since_reinforcement / 30)
access/search-hit → reinforce (t = now, confidence = max(confidence, initial·0.8))
confidence < 0.3 → archived = 1 (never deleted; reason recorded)
```

Runs as a sweep at cycle end. `cos-engine decay report` lists what faded and why.

 reinforcement events are themselves episodic rows (the system remembers remembering).

## 4b. Instinct lifecycle (§8.2) [v0.4]

Skills promote through stages on evidence: 3+ successes → confidence > 0.7 → `instinct`; 10+ → `automation` candidate. Failures demote; three consecutive failures → confidence 0.2, flag for review. Every promotion/demotion is an episodic event.

## 5. Ingest & fusion [v0.1–v0.2]

- Parse: markdown, frontmatter (YAML), wiki-links `[[..]]`, tags `#tag`.
- Chunk: semantic paragraph+heading windows, 400–800 tokens, overlap 1 sentence.
- Hash: content hash per chunk; unchanged chunks skip re-embedding (delta cycles are cheap).
- Entities [v0.2]: heading-level entities, wiki-link targets, explicit `up::`/`related::` frontmatter. Links form the graph; export writes `dot` + md.
- Sources: each vault is a `source_id`; isolation per §3.3 — queries name sources or the default corpus, never accidental cross-pollination.

## 6. MCP server (Hermes-first) [v0.2]

stdio JSON-RPC. Tools: `search`, `get_page`, `get_entity`, `graph(query)`, `recent_events`, `health`, `rebuild_status`. Config in Hermes: `mcp_servers: { cognitive-os: { command: cos-engine, args: [serve] } }`.

## 7. Runtime layout

```
~/.cognitive-os/
  brains/<name>/brain.sqlite       # one brain per project scope (§3.3 isolation)
  config.toml                       # vaults, embedder, decay, export targets
  health/cycles-<YYYY-Www>.jsonl   # the anti-silence ledger
~/.config/systemd/user/cognitive-os-sync.service + .timer   # the zero-touch loop
```

## 8. Telemetry & cost (§7, §10)

Every search answers with `_meta: { hits, tiers, ms, fused_from: {fts, vec} }`. Every cycle logs token/compute cost (embed count × per-1k price, 0 for local). The health ledger is the §10 observability contract, machine-readable from cycle one.

## 9. Failure-injection test matrix (§4.4) [v0.1]

| Injected failure | Expected |
|---|---|
| embedder dead (ollama down) | failed cycle, exit 1, ledger row; retry with backoff; no partial-embed claims |
| vault dir vanishes | warn + continue other vaults; ledger records missing |
| corrupt chunk hash mismatch | quarantine chunk, re-import page, episodic event |
| export half-written | atomic tmp+rename; cycle fails if final rename fails |
| DB locked | single-writer queue; cycle defers, never interleaves |

## 9b. Security posture (§6)

No secrets in DB or config by default (local embedder needs none). MCP server binds stdio only. Untrusted frontmatter (§6.2): wiki-links and tags parsed as data, never executed; absolute-path traversal guards on export paths; per-source write authority — the engine never writes to source vaults (read-only mounts conceptually).

## 10. Build standards (§11)

Rust 2021, `Result<T,E>` everywhere, no unwrap in production paths, functions < 50 lines target, files < 800 target, integration tests on fixture vaults committed in-tree, `cargo clippy --deny warnings` in CI, conventional commits, no AI co-author tags.

## 11. Milestone ladder

See [MILESTONES.md](MILESTONES.md) — M0 (walking skeleton) → M5 (full §3 stack), each milestone a shippable, verifiable increment with its own acceptance gates.
