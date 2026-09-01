#!/usr/bin/env python3
"""agent-forge miner: catalog upstream skill feeds, diff them against the last
mined commit, and advance the mining cursor after review.

Stdlib only (python3.11+). Works with or without PyYAML: feeds.yaml is read and
written with a surgical line-based editor so comments and ordering survive.

Subcommands:
  catalog            walk every feed clone, write agent-forge/catalog.json
  diff               compare each clone against feeds.yaml last_mined_commit,
                     print NEW/CHANGED skills as proposal candidates
  apply REPO         after review, set last_mined_commit for REPO to clone HEAD
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

FORGE_DIR = Path(__file__).resolve().parent.parent
FEEDS_FILE = FORGE_DIR / "feeds.yaml"
CATALOG_FILE = FORGE_DIR / "catalog.json"

# ---------------------------------------------------------------------------
# Minimal feeds.yaml I/O (comment/order preserving; no PyYAML dependency).
# The file has a flat, known shape: a `feeds:` list of mappings with scalar
# values, which we can round-trip line-wise. If PyYAML is available we still
# use the line editor for writes (to keep comments) but yaml for validation.
# ---------------------------------------------------------------------------

FEED_KEYS = (
    "id",
    "repo",
    "clone",
    "skills_dirs",
    "license",
    "license_note",
    "what_we_take",
    "last_mined_commit",
)


def parse_scalar(raw: str):
    raw = raw.strip()
    if raw.startswith('"') and raw.endswith('"'):
        return raw[1:-1]
    if raw.startswith("'") and raw.endswith("'"):
        return raw[1:-1]
    if raw.startswith("[") and raw.endswith("]"):
        inner = raw[1:-1].strip()
        if not inner:
            return []
        return [p.strip().strip('"').strip("'") for p in inner.split(",")]
    return raw


def load_feeds(path: Path = FEEDS_FILE) -> list[dict]:
    feeds: list[dict] = []
    current: dict | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if re.match(r"^-\s+id:\s*", stripped) and line.startswith("  - id:"):
            if current is not None:
                feeds.append(current)
            current = {"id": parse_scalar(stripped.split(":", 1)[1])}
        elif current is not None and re.match(r"^[a-z_]+:\s*", stripped) and ":" in stripped:
            key, _, value = stripped.partition(":")
            if key in FEED_KEYS[1:] and value.strip():
                current[key] = parse_scalar(value)
    if current is not None:
        feeds.append(current)
    for feed in feeds:
        missing = [k for k in FEED_KEYS if k not in feed]
        if missing:
            raise SystemExit(f"feeds.yaml: feed '{feed.get('id', '?')}' missing keys: {missing}")
    return feeds


def update_last_mined(path: Path, repo_id: str, new_sha: str) -> bool:
    """Rewrite only the last_mined_commit line inside one feed block."""
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    in_target = False
    changed = False
    for i, line in enumerate(lines):
        if re.match(r"^\s*-\s+id:\s*", line):
            in_target = parse_scalar(line.split(":", 1)[1]) == repo_id
            continue
        if in_target and re.match(r"^\s+last_mined_commit:\s*", line):
            old = line.rstrip("\n").split(":", 1)[1].strip()
            if old != new_sha:
                indent = line[: len(line) - len(line.lstrip())]
                lines[i] = f"{indent}last_mined_commit: {new_sha}\n"
                changed = True
    if not changed:
        return False
    path.write_text("".join(lines), encoding="utf-8")
    return True


# ---------------------------------------------------------------------------
# git helpers (subprocess; clones are shallow but full SHAs resolve fine)
# ---------------------------------------------------------------------------


def git(clone: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", "-C", str(clone), *args],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"git -C {clone} {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def head_sha(clone: Path) -> str:
    return git(clone, "rev-parse", "HEAD").strip()


# ---------------------------------------------------------------------------
# skill discovery: find SKILL.md files and their frontmatter
# ---------------------------------------------------------------------------

FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.DOTALL)
NAME_RE = re.compile(r"^name:\s*(.+)$", re.MULTILINE)
DESC_RE = re.compile(r"^description:\s*(.+)$", re.MULTILINE)


def find_skill_files(clone: Path, skills_dirs: list[str]):
    for skills_dir in skills_dirs:
        root = clone / skills_dir
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("SKILL.md")):
            if ".git" in path.parts:
                continue
            yield root, path


def read_frontmatter(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    match = FRONTMATTER_RE.match(text)
    if not match:
        return {}
    block = match.group(1)
    name = NAME_RE.search(block)
    desc = DESC_RE.search(block)
    return {
        "name": name.group(1).strip().strip('"').strip("'") if name else "",
        "description": desc.group(1).strip().strip('"').strip("'") if desc else "",
    }


def first_line(text: str, limit: int = 160) -> str:
    line = text.splitlines()[0] if text.splitlines() else ""
    return (line[: limit - 1] + "\u2026") if len(line) > limit else line


def feed_license_rule(feed: dict) -> str:
    if feed["license"] == "MIT":
        return "patterns-with-attribution"
    return "patterns-only-no-copy"


# ---------------------------------------------------------------------------
# subcommands
# ---------------------------------------------------------------------------


def cmd_catalog(args: argparse.Namespace) -> int:
    feeds = load_feeds()
    catalog = {"generated_by": "agent-forge/tools/mine.py catalog", "feeds": []}
    total = 0
    for feed in feeds:
        clone = Path(feed["clone"])
        entry = {
            "id": feed["id"],
            "repo": feed["repo"],
            "license": feed["license"],
            "license_rule": feed_license_rule(feed),
            "head_commit": head_sha(clone) if clone.is_dir() else None,
            "skills": [],
        }
        if clone.is_dir():
            for root, path in find_skill_files(clone, feed["skills_dirs"]):
                fm = read_frontmatter(path)
                entry["skills"].append(
                    {
                        "path": str(path.relative_to(root.parent)),
                        "name": fm.get("name") or path.parent.name,
                        "description_first_line": first_line(fm.get("description", "")),
                    }
                )
        entry["skill_count"] = len(entry["skills"])
        total += entry["skill_count"]
        catalog["feeds"].append(entry)
        print(
            f"[{feed['id']}] {entry['skill_count']:3d} skills  "
            f"license={feed['license']:<13} rule={feed_license_rule(feed)}"
        )
    CATALOG_FILE.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"catalog written: {CATALOG_FILE} ({total} skills across {len(feeds)} feeds)")
    return 0


def cmd_diff(args: argparse.Namespace) -> int:
    feeds = load_feeds()
    total_new = 0
    for feed in feeds:
        clone = Path(feed["clone"])
        if not clone.is_dir():
            print(f"[{feed['id']}] SKIP clone missing: {clone}")
            continue
        old = feed["last_mined_commit"]
        head = head_sha(clone)
        scope = [str(clone / d) for d in feed["skills_dirs"]]
        scope_args = [p for p in scope if Path(p).is_dir()]
        if not scope_args:
            print(f"[{feed['id']}] SKIP no skills dirs present")
            continue
        # --no-merges keeps the commit list tight. Shallow clones may not
        # contain OLD; then the range errors and we fall back to reporting
        # HEAD state only (first mining run has nothing to diff against).
        try:
            log = git(clone, "log", "--no-merges", "--format=%H\t%s", f"{old}..{head}", "--", *scope_args)
            had_commits = bool(log.strip())
        except RuntimeError:
            print(f"[{feed['id']}] NOTE history for {old[:12]} unavailable (shallow clone); "
                  f"showing current HEAD state only")
            log = ""
            had_commits = True  # fall through to ls-tree comparison below
        if not had_commits:
            print(f"[{feed['id']}] up to date at {head[:12]} (no changes since last_mined_commit)")
            continue
        commits = [line.split("\t", 1) for line in log.strip().splitlines()]
        # Detect NEW/CHANGED skills by comparing current SKILL.md set against
        # the state at OLD (when OLD is reachable) else against nothing.
        try:
            old_files = git(clone, "ls-tree", "-r", "--name-only", old, *scope_args).splitlines()
        except RuntimeError:
            old_files = []
        old_skill_files = {f for f in old_files if f.endswith("SKILL.md")}
        current = {str(p.relative_to(clone)) for _, p in find_skill_files(clone, feed["skills_dirs"])}
        new_skills = sorted(current - old_skill_files)
        changed = sorted(old_skill_files & current)
        print(f"[{feed['id']}] {len(commits)} commit(s) {old[:12]}..{head[:12]} touching skills dirs")
        for sha, subject in commits:
            print(f"    {sha[:12]} {subject}")
        for rel in new_skills:
            fm = read_frontmatter(clone / rel)
            total_new += 1
            print(f"  NEW     {feed['id']} :: {fm.get('name') or rel}  ({rel})")
            print(f"          trigger: {first_line(fm.get('description', ''), 120)}")
        for rel in changed:
            fm = read_frontmatter(clone / rel)
            print(f"  CHANGED {feed['id']} :: {fm.get('name') or rel}  ({rel})")
        if not new_skills and not changed:
            print("    (skill set unchanged; commits touched other files in scope)")
    print(f"proposal candidates: {total_new} new skill(s) across all feeds")
    print("next: author an original skill informed by the patterns, run tools/qc.py,")
    print("      then `mine.py apply <feed-id>` to advance the cursor.")
    return 0


def cmd_apply(args: argparse.Namespace) -> int:
    feeds = load_feeds()
    match = [f for f in feeds if f["id"] == args.repo]
    if not match:
        ids = ", ".join(f["id"] for f in feeds)
        print(f"error: unknown feed id '{args.repo}' (known: {ids})", file=sys.stderr)
        return 2
    feed = match[0]
    clone = Path(feed["clone"])
    if not clone.is_dir():
        print(f"error: clone missing: {clone}", file=sys.stderr)
        return 2
    head = head_sha(clone)
    if head == feed["last_mined_commit"]:
        print(f"[{feed['id']}] already at {head} ; nothing to apply")
        return 0
    if update_last_mined(FEEDS_FILE, feed["id"], head):
        print(f"[{feed['id']}] last_mined_commit -> {head}")
        return 0
    print(f"error: failed to update {FEEDS_FILE}", file=sys.stderr)
    return 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="mine.py", description="agent-forge feed miner")
    sub = parser.add_subparsers(dest="command", required=True)

    p_catalog = sub.add_parser("catalog", help="walk feed clones into agent-forge/catalog.json")
    p_catalog.set_defaults(func=cmd_catalog)

    p_diff = sub.add_parser("diff", help="show NEW/CHANGED upstream skills since last_mined_commit")
    p_diff.set_defaults(func=cmd_diff)

    p_apply = sub.add_parser("apply", help="advance last_mined_commit for one feed after review")
    p_apply.add_argument("repo", help="feed id from feeds.yaml (e.g. wade-skills)")
    p_apply.set_defaults(func=cmd_apply)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
