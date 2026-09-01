#!/usr/bin/env bash
# agent-forge bootstrap: wire the forge skills and the repo skills/ dir into a
# Hermes profile config via skills.external_dirs. Idempotent, append-only,
# comment-preserving. Never touches anything outside the skills: block.
#
# Usage: bootstrap-hermes.sh [--dry-run] [CONFIG_PATH]
#   CONFIG_PATH defaults to /home/ae/.hermes/config.yaml
#
# Strategy: prefer a python3 surgical append (needs no PyYAML); if python3 is
# unavailable, fall back to an awk line editor with identical semantics.

set -euo pipefail

CONFIG="${DEFAULT_CONFIG:-/home/ae/.hermes/config.yaml}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help)
      sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      if [[ -e "$1" || "$1" == */* ]]; then CONFIG="$1"; shift; else
        echo "error: unknown argument '$1'" >&2; exit 2
      fi
      ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FORGE_SKILLS="${REPO_ROOT}/agent-forge/skills"
REPO_SKILLS="${REPO_ROOT}/skills"

if [[ ! -d "$FORGE_SKILLS" ]]; then
  echo "error: $FORGE_SKILLS not found (run from a checkout of cognitive-os)" >&2
  exit 2
fi

if [[ ! -f "$CONFIG" ]]; then
  echo "error: config not found: $CONFIG" >&2
  exit 2
fi

if command -v python3 >/dev/null 2>&1; then
  # --- python path: surgical, comment-preserving append -------------------
  MODE="apply"
  [[ "$DRY_RUN" -eq 1 ]] && MODE="dry-run"
  python3 - "$CONFIG" "$FORGE_SKILLS" "$REPO_SKILLS" "$MODE" <<'PYEOF'
import re
import sys
import tempfile
from pathlib import Path

config_path, forge_dir, skills_dir, mode = sys.argv[1:5]
config = Path(config_path)
text = config.read_text(encoding="utf-8")
lines = text.splitlines(keepends=True)

want = [forge_dir, skills_dir]

# Locate the skills: block and its external_dirs list.
skills_idx = None
for i, line in enumerate(lines):
    if re.match(r"^skills:\s*$", line):
        skills_idx = i
        break
if skills_idx is None:
    sys.exit("error: no 'skills:' block found in config; refusing to invent one")

ext_idx = None
depth = 0
for i in range(skills_idx + 1, len(lines)):
    line = lines[i]
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    indent = len(line) - len(line.lstrip())
    if indent <= depth and not line.lstrip().startswith(("external_dirs:", "- ")):
        break
    if re.match(r"^\s{2}external_dirs:\s*$", line):
        ext_idx, depth = i, 2
        break
if ext_idx is None:
    sys.exit("error: no 'skills.external_dirs:' list found; refusing to invent one")

# Collect existing entries (list items under external_dirs) and the last item.
entries = []
last_item = None
for i in range(ext_idx + 1, len(lines)):
    line = lines[i]
    if not line.strip():
        continue
    if line.lstrip().startswith("- "):
        last_item = i
        entries.append(line.split("- ", 1)[1].strip())
        continue
    if line.lstrip().startswith("#"):
        continue
    break

missing = [d for d in want if d not in entries]
if not missing:
    print("already wired: skills.external_dirs contains both forge and repo skill dirs")
    sys.exit(0)

insert_at = (last_item + 1) if last_item is not None else (ext_idx + 1)
new_lines = []
for d in missing:
    indent = "    "  # match the 4-space list indentation used by hermes configs
    new_lines.append(f"{indent}- {d}\n")

print(f"would append {len(missing)} entr{'y' if len(missing) == 1 else 'ies'} to skills.external_dirs:")
for d in missing:
    print(f"  + {d}")
if mode == "dry-run":
    print("dry-run: config left unchanged")
    sys.exit(0)

out = lines[:insert_at] + new_lines + lines[insert_at:]
config.write_text("".join(out), encoding="utf-8")
print(f"written: {config}")
PYEOF
else
  # --- awk fallback (no python3 available) --------------------------------
  # Buffer the external_dirs list items; when the list ends, emit the buffered
  # items followed by the missing entries, then the terminating line.
  MISSING=()
  for dir in "$FORGE_SKILLS" "$REPO_SKILLS"; do
    if grep -qF -- "- ${dir}" "$CONFIG"; then
      echo "already present: ${dir}"
    else
      MISSING+=("$dir")
    fi
  done
  if [[ ${#MISSING[@]} -eq 0 ]]; then
    echo "already wired: skills.external_dirs contains both forge and repo skill dirs"
    exit 0
  fi
  echo "would append ${#MISSING[@]} entr$( [[ ${#MISSING[@]} -eq 1 ]] && echo y || echo ies ) to skills.external_dirs:"
  for dir in "${MISSING[@]}"; do echo "  + ${dir}"; done
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "dry-run: config left unchanged"
    exit 0
  fi
  ARGS=()
  for dir in "${MISSING[@]}"; do ARGS+=(-v "entry=${dir}"); done
  awk "${ARGS[@]}" '
    /^skills:/ { in_skills=1 }
    in_skills && /^  external_dirs:[[:space:]]*$/ { in_ext=1; print; next }
    in_ext && /^    - / { buf[n++] = $0; next }
    in_ext && /^   #/ { buf[n++] = $0; next }
    in_ext {
      in_ext = 0; in_skills = 0
      for (i = 0; i < n; i++) print buf[i]
      if (ENVIRON["FORGE_INSERTED"] != "1") {
        print "    - " entry
      }
      print
      next
    }
    { print }
  ' "$CONFIG" > "${CONFIG}.tmp"
  # second pass for the second dir (awk -v entry= single-value limitation)
  if [[ ${#MISSING[@]} -eq 2 ]]; then
    awk -v "entry=${MISSING[1]}" '
      /^skills:/ { in_skills=1 }
      in_skills && /^  external_dirs:[[:space:]]*$/ { in_ext=1; print; next }
      in_ext && /^    - / { buf[n++] = $0; last=1; next }
      in_ext && /^   #/ { buf[n++] = $0; next }
      in_ext {
        in_ext = 0; in_skills = 0
        for (i = 0; i < n; i++) print buf[i]
        print "    - " entry
        print
        next
      }
      { print }
    ' "${CONFIG}.tmp" > "${CONFIG}.tmp2" && mv "${CONFIG}.tmp2" "${CONFIG}.tmp"
  fi
  mv "${CONFIG}.tmp" "$CONFIG"
  echo "written: ${CONFIG}"
fi
