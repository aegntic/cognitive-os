#!/usr/bin/env python3
"""agent-forge QC gate: lint a skill directory before it can enter the registry.

Stdlib only (python3.11+). Checks:
  a) frontmatter   name matches [a-z0-9-]+ ; description present, 80-400 chars,
                   opens with a use-when trigger ("Use when..." / "Use ... when")
  b) content scan  no em-dashes, no praise-words, no TODO/FIXME, no copied-code
                   copyright markers (attribution belongs in ATTRIBUTION.md only)
  c) hygiene       no file over 200KB, no .venv or node_modules directories

Exit 0 on pass with a report; exit 1 with the failure report otherwise.
Scans text files only (SKILL.md, *.md, *.py, *.sh, *.json, *.yaml, *.yml, *.txt).
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MAX_FILE_BYTES = 200 * 1024

BANNED_DIRS = {".venv", "node_modules"}

TEXT_SUFFIXES = {".md", ".py", ".sh", ".json", ".yaml", ".yml", ".txt", ".toml", ".cfg", ".ini"}

# Praise-words: rejected when used as filler praise. "robust" gets a word-
# boundary match everywhere; the others are rejected outright (they are
# essentially always filler in skill prose).
PRAISE_WORDS = ("delve", "tapestry", "seamlessly")
PRAISE_BOUNDARY = ("robust",)

COPIED_CODE_MARKERS = (
    "Copyright (c) Zapier",
    "Copyright (c) BuilderIO",
)

FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?", re.DOTALL)
NAME_RE = re.compile(r"^name:\s*(.+)$", re.MULTILINE)
DESC_RE = re.compile(r"^description:\s*(.+)$", re.MULTILINE)

TRIGGER_RE = re.compile(r"^\s*(use (this skill )?when|use when)\b", re.IGNORECASE)

# Em-dash and its less common typographic cousins that signal pasted prose.
EM_DASH = "\u2014"
DASH_COUSINS = ("\u2013", "\u2212")

NAME_VALID_RE = re.compile(r"^[a-z0-9-]+$")


class Report:
    def __init__(self, skill_dir: Path):
        self.skill_dir = skill_dir
        self.failures: list[str] = []
        self.notes: list[str] = []

    def fail(self, check: str, detail: str) -> None:
        self.failures.append(f"[{check}] {detail}")

    def note(self, check: str, detail: str) -> None:
        self.notes.append(f"[{check}] {detail}")

    @property
    def ok(self) -> bool:
        return not self.failures

    def render(self) -> str:
        lines = [f"qc.py report for {self.skill_dir}"]
        lines.append(f"result: {'PASS' if self.ok else 'FAIL'} "
                     f"({len(self.failures)} failure(s), {len(self.notes)} note(s))")
        for item in self.failures:
            lines.append(f"  FAIL {item}")
        for item in self.notes:
            lines.append(f"  note {item}")
        if not self.failures and not self.notes:
            lines.append("  (clean)")
        return "\n".join(lines)


def iter_files(root: Path):
    """Yield files worth scanning. Skips .git internals and does not descend
    into banned dirs (their presence is itself a hygiene failure)."""
    for path in sorted(root.rglob("*")):
        if path.is_file() and not any(p in BANNED_DIRS | {".git"} for p in path.parts):
            yield path


def check_frontmatter(report: Report, skill_md: Path) -> None:
    text = skill_md.read_text(encoding="utf-8", errors="replace")
    match = FRONTMATTER_RE.match(text)
    if not match:
        report.fail("frontmatter", f"{skill_md.name}: no frontmatter block found")
        return
    block = match.group(1)

    name = NAME_RE.search(block)
    if not name:
        report.fail("frontmatter", "name: is missing")
    else:
        value = name.group(1).strip().strip('"').strip("'")
        if not NAME_VALID_RE.match(value):
            report.fail("frontmatter", f"name '{value}' must match [a-z0-9-]+")

    desc = DESC_RE.search(block)
    if not desc:
        report.fail("frontmatter", "description: is missing")
        return
    # Unwrap single or double quoting.
    raw = desc.group(1).strip()
    if len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in "\"'":
        raw = raw[1:-1]
    # Fold newlines so multi-line YAML scalars measure as one string.
    raw = " ".join(raw.split())
    length = len(raw)
    if not 80 <= length <= 400:
        report.fail("frontmatter", f"description length {length} outside 80-400 chars")
    if not TRIGGER_RE.match(raw):
        report.fail("frontmatter", "description must start with a use-when trigger (e.g. 'Use when ...')")


def check_content(report: Report, path: Path) -> None:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        report.fail("hygiene", f"{path.relative_to(report.skill_dir)}: unreadable: {exc}")
        return
    rel = path.relative_to(report.skill_dir)

    if EM_DASH in text:
        count = text.count(EM_DASH)
        report.fail("content", f"{rel}: {count} em-dash character(s); use a comma or hyphen instead")

    for cousin in DASH_COUSINS:
        if cousin in text:
            report.fail("content", f"{rel}: typographic dash U+{ord(cousin):04X} present; use plain '-'")

    lowered = text.lower()
    for word in PRAISE_WORDS:
        if re.search(rf"\b{word}\b", lowered):
            report.fail("content", f"{rel}: praise-word '{word}'")

    for word in PRAISE_BOUNDARY:
        for m in re.finditer(rf"\b{word}\b", lowered):
            report.fail("content", f"{rel}: praise-word '{word}' (line {text[:m.start()].count(chr(10)) + 1})")
            break  # one report per file is enough

    for marker in ("TODO", "FIXME"):
        if re.search(rf"\b{marker}\b", text):
            report.fail("content", f"{rel}: unresolved {marker} marker")

    for marker in COPIED_CODE_MARKERS:
        if marker.lower() in lowered:
            report.fail("content", f"{rel}: copied-code marker '{marker}' (attribution belongs in ATTRIBUTION.md)")


def check_hygiene(report: Report, root: Path) -> None:
    for path in sorted(root.rglob("*")):
        if path.is_dir() and path.name in BANNED_DIRS:
            report.fail("hygiene", f"banned directory present: {path.relative_to(root)}")

    for path in iter_files(root):
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            report.fail("hygiene", f"{path.relative_to(root)}: {size} bytes exceeds {MAX_FILE_BYTES} limit")
        elif size > 0.8 * MAX_FILE_BYTES:
            report.note("hygiene", f"{path.relative_to(root)}: {size} bytes is within 20% of the size cap")


def run_qc(skill_dir: Path) -> Report:
    report = Report(skill_dir)
    if not skill_dir.is_dir():
        report.fail("structure", f"not a directory: {skill_dir}")
        return report
    skill_md = skill_dir / "SKILL.md"
    if not skill_md.is_file():
        report.fail("structure", f"SKILL.md missing in {skill_dir}")
    else:
        check_frontmatter(report, skill_md)

    for path in iter_files(skill_dir):
        if path.suffix.lower() in TEXT_SUFFIXES:
            check_content(report, path)

    check_hygiene(report, skill_dir)
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="qc.py", description="agent-forge skill QC gate")
    parser.add_argument("skill_dir", type=Path, help="path to the skill directory to lint")
    parser.add_argument("--quiet", action="store_true", help="only print the result line")
    args = parser.parse_args(argv)

    report = run_qc(args.skill_dir)
    if args.quiet:
        print(f"{'PASS' if report.ok else 'FAIL'} {args.skill_dir}")
    else:
        print(report.render())
    return 0 if report.ok else 1


if __name__ == "__main__":
    sys.exit(main())
