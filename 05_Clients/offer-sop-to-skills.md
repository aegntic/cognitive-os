---
id: sop-to-skills
title: 'Turn Your SOPs Into a Running Agent System'
state: draft
---

## SOP-to-Skills

Your standard operating procedures are already written. They are also
already dead: nobody re-reads a SOP folder, new hires learn by asking
the person who wrote them, and the processes drift.

We convert your SOP library into a running agent system: each recurring
procedure becomes a versioned, quality-gated skill an agent executes on
schedule, with monitoring attached from day one.

## What gets delivered

1. **SOP audit** (week 1): we read your procedures, rank them by
   recurrence x failure cost, and select the 3-5 worth automating first.
   You approve the list before anything is built.
2. **Skill conversion** (weeks 2-3): each selected SOP becomes a
   machine-executable skill with an explicit definition of done,
   verification gates, and a rollback path. Built on the open-source
   Hermes agent stack.
3. **Schedules and watchers** (week 4): the skills run themselves:
   daily briefs, weekly reports, follow-ups. Every automated job has a
   monitoring check that notices when it silently stops working,
   because broken automation is worse than no automation.
4. **Handover**: your team gets the system, the documentation, and the
   ability to modify it. It runs from a clean checkout without us.

## What it costs

- **Setup**: 10,000 to 25,000 USD depending on SOP count and
  integration depth (fixed after the audit; no scope drift).
- **Monitoring retainer**: 1,000 to 2,000 USD/month. Optional, and
  the only ongoing relationship: we watch the watchers, fix drift,
  and convert the next SOP when you say so.

Anchors, honestly sourced: these price points come from published
playbooks in the agent-services market [unverified as market-wide
rates; they are our opening anchors, negotiable per engagement].

## Why this works when your automations usually rot

- Every skill carries a **definition of done** written before the work
  starts: machine-checkable acceptance criteria, not "looks right".
- The **worker never grades its own homework**: completion is verified
  by a gate or a second pass, never by the automation's self-report.
- **Monitoring is installed at build time**, not bolted on after the
  first silent failure.

## Proof this is not slideware

The method is public and inspectable:

- The skill pipeline: github.com/aegntic/cognitive-os (agent-forge)
- The quality gates that run on every deliverable: same repo,
  agent-forge/tools/qc.py
- A working end-to-end skill with examples: github.com/aegntic/tab-harvest

## Fit check (be honest)

This is for you if: you have 10+ written SOPs, a team that follows
them most of the time, and recurring processes with real failure costs.
It is not for you if: your processes live in one person's head, or you
want "an AI strategy" rather than specific jobs executed reliably.

Next step: a 30-minute call, then the SOP audit.
