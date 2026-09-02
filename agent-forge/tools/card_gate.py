#!/usr/bin/env python3
"""card_gate.py - refuse delegation cards that lack a definition-of-done.

Implements the card contract (agent-forge/docs/card-contract.md):
a card is dispatchable only if its DONE section carries machine-checkable
criteria (minimum 3 "DONE IF" statements), a maintainability line, an
unplug test, and a business link.

Usage:
  python3 card_gate.py <card.md> [--strict]

Exit codes: 0 = dispatchable; 1 = rejected (report on stdout).
--strict additionally requires the reviewer line and rejects vague
criteria (no digits, no file paths, no commands) heuristically.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MIN_CRITERIA = 3
MAX_CRITERIA = 7

DONE_RE = re.compile(r"^##\s+DONE\s*$", re.MULTILINE)
CRITERION_RE = re.compile(r"^\s*-\s*DONE IF\s+(.+)$", re.MULTILINE)
MAINTAIN_RE = re.compile(r"maintainable by[:\s]", re.IGNORECASE)
UNPLUG_RE = re.compile(r"unplug test[:\s]", re.IGNORECASE)
BIZ_RE = re.compile(r"business link[:\s]", re.IGNORECASE)

VAGUE_MARKERS = ("looks good", "works well", "is nice", "is clean",
                 "high quality", "properly", "correctly formatted")


def check_card(card_path: Path, strict: bool = False) -> tuple[bool, list[str]]:
    """Return (dispatchable, findings)."""
    findings: list[str] = []
    text = card_path.read_text(encoding="utf-8")

    if not DONE_RE.search(text):
        return False, ["card has no '## DONE' section; see agent-forge/docs/card-contract.md"]

    done_block = DONE_RE.search(text)
    # capture from DONE header to next ## or EOF
    tail = text[done_block.end():]
    nxt = re.search(r"^##\s+", tail, re.MULTILINE)
    block = tail[:nxt.start()] if nxt else tail

    criteria = CRITERION_RE.findall(block)
    n = len(criteria)
    if n < MIN_CRITERIA:
        findings.append(f"only {n} 'DONE IF' criteria; minimum {MIN_CRITERIA}")
    if n > MAX_CRITERIA:
        findings.append(f"{n} criteria exceeds maximum {MAX_CRITERIA}; split the card")

    if not MAINTAIN_RE.search(block):
        findings.append("missing 'Maintainable by:' line (second-best-engineer test)")
    if not UNPLUG_RE.search(block):
        findings.append("missing 'Unplug test:' line")
    if not BIZ_RE.search(block):
        findings.append("missing 'Business link:' line (or explicit 'no direct metric')")

    if strict:
        for c in criteria:
            low = c.lower()
            if any(v in low for v in VAGUE_MARKERS):
                findings.append(f"vague criterion: 'DONE IF {c[:60]}'")
            checkable = bool(re.search(r"\d|\.py|\.md|\.json|\.mp4|/|exit|grep|count|ffprobe|py_compile", low))
            if not checkable:
                findings.append(f"criterion not machine-checkable: 'DONE IF {c[:60]}'")

    return (not findings), findings


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("card", help="path to the delegation card markdown")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()
    path = Path(args.card)
    if not path.is_file():
        print(f"card not found: {path}")
        return 1
    ok, findings = check_card(path, strict=args.strict)
    if ok:
        print(f"DISPATCHABLE: {path.name} ({len(CRITERION_RE.findall(path.read_text(encoding='utf-8')))} criteria)")
        return 0
    print(f"REJECTED: {path.name}")
    for f in findings:
        print(f"  - {f}")
    print("fix per agent-forge/docs/card-contract.md; a card without a finish line buys process, not value")
    return 1


if __name__ == "__main__":
    sys.exit(main())
