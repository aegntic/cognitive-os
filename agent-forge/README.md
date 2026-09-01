# Agent Forge

The forge is the self-evolving skill pipeline of cognitive-os: it turns movement in upstream skill repositories into original, QC-gated skills in this repo. It exists because Layer 8 of AGENTS.md (Self-Improvement Pipeline) already demands that confirmed patterns be encoded as skills; the forge is the machinery that does so deliberately instead of ad hoc, with license discipline and a quality gate.

The forge never copies. Upstream feeds contribute patterns; every forge skill is authored fresh, its provenance recorded in registry.yaml and ATTRIBUTION.md, and its body passed through an automated QC gate before it can be promoted.

## The Loop

```
    six upstream feeds (feeds.yaml)
              |
              v
   [1] MINE --- mine.py catalog / diff
              |   NEW + CHANGED upstream skills
              v
   [2] PROPOSE  feed-digest (weekly memo)
              |   cluster by theme, rank, decline with reasons
              v
   [3] AUTHOR  original skill informed by pattern
              |   frontmatter: use-when trigger, provenance, MIT
              v
   [4] QC ----- qc.py gate (frontmatter, content, hygiene)
              |   PASS required; FAIL goes back to AUTHOR
              v
   [5] REVIEW  council-of-skills (adversarial panel + BUYER)
              |   verdict always names a cheapest 48h test
              v
   [6] MERGE - registry.yaml entry (status: seed)
              |   skill-steward audits lifecycle later
              v
   back to [1]: mine.py apply advances the cursor
```

## Layout

1. `feeds.yaml` - the six upstream repos, their license rule (MIT = patterns with attribution; PATTERNS-ONLY = nothing may be taken), and the `last_mined_commit` cursor per feed.
2. `registry.yaml` - canonical registry of forge-tracked skills with provenance, status (seed / stable / quarantined / planned / external-canonical), and verification dates.
3. `catalog.json` - generated inventory of every upstream skill across the feeds (do not edit by hand).
4. `tools/mine.py` - the miner: `catalog`, `diff`, `apply`.
5. `tools/qc.py` - the gate: frontmatter lint, forbidden-content scan, file hygiene.
6. `tools/bootstrap-hermes.sh` - idempotent wiring of forge + repo skills into a Hermes profile's `skills.external_dirs`.
7. `skills/` - forge-authored original skills (each passed qc.py).
8. `fixtures/broken-skill/` - deliberately broken fixture proving the QC gate fails bad input; never fix it, never deploy it.
9. `ATTRIBUTION.md` - one line per pattern sourced from a feed, with license basis.

## Usage

```bash
# refresh the upstream inventory
python3 agent-forge/tools/mine.py catalog

# what changed upstream since the last mining pass
python3 agent-forge/tools/mine.py diff

# after reviewing proposals, advance one feed's cursor
python3 agent-forge/tools/mine.py apply wade-skills

# gate a skill (exit 1 on failure with a full report)
python3 agent-forge/tools/qc.py agent-forge/skills/council-of-skills

# wire forge + repo skills into a Hermes profile (append-only, idempotent)
agent-forge/tools/bootstrap-hermes.sh --dry-run ~/.hermes/config.yaml
agent-forge/tools/bootstrap-hermes.sh ~/.hermes/config.yaml
```

All tools are stdlib-only python3 (3.11+) or bash; no dependencies to install.

## Skills Shipped by the Forge

1. `council-of-skills` - convene registry skills as adversarial reviewers of a proposal; a BUYER persona states whether they would pay, and every verdict names the cheapest 48-hour test.
2. `feed-digest` - weekly feed review: mine, cluster by theme, ranked proposal memo.
3. `skill-steward` - lifecycle audit of installed skills: staleness, overlap, trigger conflicts; proposes retire / promote / merge / quarantine.

## Integration with cognitive-os

AGENTS.md (Layer 8, Self-Improvement Pipeline) defines the instinct-to-skill lifecycle; the forge is its concrete implementation. Skills authored here flow into Hermes via `bootstrap-hermes.sh`, and the repo's own `skills/` directory (repomix-explorer, veritas-operator) is tracked in the registry alongside forge originals.

## Rules

1. Never copy from a feed, not even a sentence. Patterns only, recorded in ATTRIBUTION.md.
2. Never take anything from a PATTERNS-ONLY feed (connectors, agent-native); they are cataloged for awareness only.
3. No skill enters the registry without a passing qc.py run.
4. No invented metrics: the forge reports what mine.py measured, nothing else.
