---
name: feed-digest
description: 'Use when it is time for the weekly upstream feed review, or when the user asks what changed in the skill feeds: runs the forge miner diff, clusters new and changed upstream skills by theme, and writes a ranked proposal memo of which patterns are worth authoring into originals.'
tags: [agent-forge, mining, digest, weekly, proposals, clustering]
version: 0.1.0
license: MIT
metadata:
  provenance: 'ranked-memo and evidence-first reporting pattern inspired by zapier/gtm-cheat-codes/skills (campaign-postmortem, daily-lead-steward) (MIT)'
  forge_status: seed
---

# Feed Digest

Turn a week of upstream feed movement into a one-page, ranked proposal memo. This is the intake side of the forge loop: it decides which upstream patterns deserve the cost of authoring an original skill, and it makes that decision in writing so it can be audited later.

## When to Run

1. Weekly cadence (the default): every Monday or at the user's standing review slot.
2. On demand: the user asks "what's new in the feeds" or "any patterns worth stealing".
3. Before a forge planning session: the memo is the agenda.

## Inputs

```yaml
feeds_file: # Default agent-forge/feeds.yaml.
window: # Human label for the period, e.g. "2026-W36". Used in the memo header only.
max_proposals: # Default 5. The memo is a ranking, not a census.
```

## Protocol

### Step 1: Mine

Run the diff and capture its output verbatim:

```bash
python3 agent-forge/tools/mine.py catalog   # refresh catalog.json first
python3 agent-forge/tools/mine.py diff      # NEW/CHANGED skills per feed since last_mined_commit
```

If every feed prints "up to date", stop and write a two-line memo saying so. An empty digest is a valid digest; do not pad it.

### Step 2: Cluster by Theme

Group every NEW and CHANGED upstream skill into themes by what problem they solve, not by which repo they came from. Three to six themes is the honest range:

1. **Cluster key**: the theme name in the user's vocabulary ("browser control", "evidence discipline").
2. **Members**: feed id + skill name + one-line trigger summary from the catalog.
3. **License gate**: check feeds.yaml per member. MIT feeds allow pattern derivation with attribution; PATTERNS-ONLY feeds (connectors, agent-native) contribute awareness only and are marked as such in the memo.

### Step 3: Rank into Proposals

Score each theme on three axes, each 1-5, and record the product:

1. **Fit**: does it fill a real gap in agent-forge/registry.yaml, or duplicate an existing tracked skill?
2. **Trigger clarity**: will the use-when condition actually fire in real sessions? Vague triggers score 1.
3. **Cost**: inverse of authoring plus maintenance burden (a skill that wraps one command scores high).

Write the memo in this shape (markdown, one page):

```markdown
# Feed Digest {window}

## Headline

One sentence: what moved upstream and what it means for the forge.

## Themes

- {theme}: {members, with license rule}
- ...

## Ranked Proposals

1. {proposal} (score {n}/125): what original skill to author, which pattern inspires it,
   the trigger sentence, and the first QC acceptance check.
2. ...

## Declined

- {theme or skill}: one-line reason it was declined (duplicate, vague trigger, license).
```

Rules for the ranking:

1. Every proposal names its upstream inspiration and its license rule, and is attribution-ready for ATTRIBUTION.md.
2. Every proposal carries a falsifiable trigger sentence; if you cannot write one, it is not a proposal.
3. Declined items are listed with reasons. Silence is not a decision record.
4. Never propose copying. The memo's unit of value is the pattern, not the file.

### Step 4: Hand Off

1. For each accepted proposal, create or update a `planned` entry in agent-forge/registry.yaml.
2. Convene `council-of-skills` on the top proposal if two or more proposals compete for the same gap.
3. After the memo is reviewed and acted on, advance the cursors: `python3 agent-forge/tools/mine.py apply <feed-id>` per reviewed feed.

## Hard Rules

1. The memo is evidence-first: every claim traces to a line of `mine.py diff` output.
2. No invented counts, no adoption metrics, no engagement numbers.
3. One page. If it does not fit, the ranking was not ruthless enough.
