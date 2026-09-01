---
name: council-of-skills
description: 'Use when a proposed skill, workflow, or decision needs adversarial review before it ships: convenes reviewers drawn from the agent-forge registry as hostile personas, adds a BUYER who states plainly whether they would pay, and forces every verdict to include the cheapest 48-hour test that could kill or validate the idea.'
tags: [agent-forge, review, adversarial, council, decision, registry]
version: 0.1.0
license: MIT
metadata:
  provenance: 'adversarial council pattern inspired by zapier/wade-skills/skills/war-council (MIT); BUYER persona and cheapest-48-hour-test verdict rule inspired by Nate Herk roast council (public talk, 2026-06-25)'
  forge_status: seed
---

# Council of Skills

Convene the agent-forge registry itself as a panel of adversarial reviewers for a proposed skill, workflow, or decision. Each reviewer is a tracked skill playing a hostile persona: it argues from its own specialty, hunts the failure modes it knows best, and is explicitly not paid to agree. A BUYER persona speaks for the target user in plain money terms. A judge synthesizes dissent into one verdict that always names the cheapest test of the idea.

The point: replace the "everyone in the room agrees with the author" failure mode with a panel whose job is to find the hole before the user does.

## When to Convene

1. A new skill is proposed to the forge (post-authoring, pre-merge QC review).
2. A registry decision is contested: retire, promote, merge, or quarantine.
3. A plan or architectural choice is T2 or above and the cost of being wrong is real.

Do NOT convene for trivial edits, formatting, or reversible low-stakes choices; a plain review is cheaper.

## Inputs

```yaml
subject: # REQUIRED. Path to the proposed skill dir, or the decision stated in one paragraph.
context: # Why now, what triggered the proposal, what breaks if it is wrong.
audience: # Who is supposed to benefit (the BUYER role-plays them).
registry: # Default agent-forge/registry.yaml; override for a different panel pool.
panel_size: # Default 4 registry reviewers + 1 BUYER + 1 judge. Keep it odd and small.
```

## Protocol

### Step 1: Draw the Panel from the Registry

Read `agent-forge/registry.yaml` and select reviewers whose specialty differs from both each other and the subject. Prefer disagreement: if the subject is a mining tool, pull a QC-oriented skill and a writing-oriented skill, not three miners. One-line role cards:

1. **Practitioner**: has this problem daily; attacks usability and hidden setup cost.
2. **Skeptic**: attacks the premise; asks what evidence supports the trigger ever firing.
3. **Maintainer**: attacks lifecycle cost; who updates this when the upstream feed moves.
4. **Neighbor**: the registry skill closest in scope; attacks overlap and trigger conflicts.
5. **BUYER**: role-plays the target customer. States plainly whether they would pay for this (in money, time saved, or risk removed), and gives their single strongest objection if not.

Each reviewer speaks once, in character, maximum 120 words. Reviewers may not reference each other before the BUYER has spoken (prevents anchoring).

### Step 2: Verdict

The judge (you, dropping all personas) writes the verdict in this exact shape:

```yaml
verdict: adopt | adapt | reshape | kill
strongest_objection: # the one argument that survived, stated steel-manned
buyer_call: # would pay / would not pay, plus the objection in the BUYER's words
conditions: # concrete changes required for adopt/adapt
cheapest_48h_test: # REQUIRED ALWAYS, including when verdict is reshape or kill:
  # the single lowest-cost experiment runnable within 48 hours
  # that would validate or kill the idea outright
```

The `cheapest_48h_test` is mandatory even when the verdict is `kill`. A killed idea that deserves a cheap test gets one; a reshape without a falsifiable next step is just procrastination. Good tests name their sample, their instrument, and their kill threshold ("run qc.py over 10 unrelated installed skills; if fewer than 3 fail for real reasons, the gate is too loose").

### Step 3: Record

Append the verdict (one YAML block) to the subject skill's registry entry as `last_council:` with date, verdict, and the 48-hour test. Registry entries with a failing `cheapest_48h_test` outcome move to `quarantined` on the next `skill-steward` audit.

## Hard Rules

1. Never let the author of the subject speak as a panel member.
2. Never synthesize before every reviewer (including the BUYER) has spoken.
3. Never issue a verdict without the `cheapest_48h_test` field filled.
4. Dissent is data: if two reviewers directly contradict, record both claims in the verdict rather than averaging them.
5. The council reviews patterns and prose, never copies them; upstream-derived wording goes through ATTRIBUTION.md.
