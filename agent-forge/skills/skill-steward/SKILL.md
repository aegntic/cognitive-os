---
name: skill-steward
description: 'Use when installed skills need a lifecycle audit: scans the forge registry and the configured skill directories for staleness, overlap, and conflicting triggers, then proposes a retire, promote, merge, or quarantine action per skill with evidence.'
tags: [agent-forge, lifecycle, audit, hygiene, registry, stewardship]
version: 0.1.0
license: MIT
metadata:
  provenance: 'original design for the agent-forge lifecycle; no upstream body derived'
  forge_status: seed
---

# Skill Steward

Audit the installed skill base and propose lifecycle actions per skill. The steward never edits or deletes anything itself: it reads, verifies, and writes a proposal memo. Execution is a separate human-approved step. This is the pruning side of the forge loop; unpruned skill libraries rot into trigger conflicts nobody can debug.

## When to Run

1. Monthly cadence, or when the registry passes roughly thirty tracked skills.
2. After a bulk import (feeds wiring, machine migration, profile sharing).
3. When the agent fires the wrong skill or fires two skills for one request.

## Inputs

```yaml
registry: # Default agent-forge/registry.yaml.
skill_dirs: # Default: the dirs listed in the Hermes profile's skills.external_dirs,
  # plus agent-forge/skills and the repo skills/ dir. Override to scope.
max_actions: # Default 10. An audit that proposes everything proposes nothing.
```

## Protocol

### Step 1: Inventory

Walk each skill dir, collect every SKILL.md, and parse frontmatter: name, description, version, tags, provenance. Cross-reference against registry.yaml. Record three gap classes:

1. **Untracked**: on disk, absent from the registry (decision needed: track or ignore).
2. **Ghost**: in the registry, absent on disk (decision needed: relink or retire).
3. **External-canonical**: tracked by reference only (example: aegntic/tab-harvest); verified by URL, never duplicated into the forge.

### Step 2: Staleness Signals

Per skill, gather honest signals without inventing precision:

1. `last_verified` age in registry.yaml (days since).
2. File mtime of SKILL.md.
3. Presence of unresolved QC failures (run `python3 agent-forge/tools/qc.py <dir>`).
4. Broken references: internal links or scripts that no longer exist.
5. Version still 0.1.0 with no recorded verification history.

Do not fabricate usage counts. If no signal exists, the memo says "no signal"; that is itself a finding (undocumented skills decay silently).

### Step 3: Overlap and Conflict Detection

1. **Trigger overlap**: two skills whose use-when conditions can fire on the same request. Extract the trigger clause (first sentence of description) and compare pairwise within shared tag groups. Flag pairs sharing two or more significant words.
2. **Shadowing**: identical `name:` in two configured dirs; Hermes resolves by path order, which the user cannot see. Flag all duplicates.
3. **Merge candidates**: overlapping skills whose union fits one trigger sentence with an OR of scopes.
4. **Conflict with provenance**: a skill that claims a pattern source its body does not reflect, or an ATTRIBUTION.md entry with no corresponding skill.

### Step 4: Action Proposal Memo

One page, one action per line, strongest first:

```markdown
# Skill Steward Audit {date}

## Summary

{N} skills scanned across {D} dirs; {X} actions proposed, {Y} deferred for lack of signal.

## Proposed Actions

1. RETIRE {skill}: {evidence} -> {disposition: archive path or registry status flip}
2. PROMOTE {skill}: seed -> stable: {evidence: QC pass history + verification events}
3. MERGE {a} + {b} -> {name}: {shared trigger sentence}
4. QUARANTINE {skill}: {reason: QC fail, ghost references, provenance mismatch}
5. TRACK {untracked dir}: {why it belongs in the registry}

## Registry Deltas (apply after approval)

{exact YAML lines to add or change}
```

Rules:

1. Every action cites its evidence lines from Steps 1 to 3.
2. RETIRE never deletes: archive with a dated reason (instincts are archived, not destroyed).
3. PROMOTE requires at least one passing QC run and one real-session verification.
4. QUARANTINE is reversible; RETIRE of an archived skill requires the human, not the steward.
5. Cap the memo at `max_actions`; rank the rest as deferred with one-line reasons.

### Step 5: Hand Off

1. Present the memo. Wait for explicit approval before any mutation.
2. On approval, apply the registry deltas exactly as written, then re-run the audit scoped to the touched skills to confirm the deltas landed.
3. Contentious calls (merge chains, retire of anything with provenance) go to `council-of-skills` instead of the memo.

## Hard Rules

1. The steward proposes; the human disposes. No direct edits to skill bodies.
2. Never move a skill out of an external feed clone; feeds are read-only.
3. One audit, one memo: no side-channel fixes while the audit is open.
