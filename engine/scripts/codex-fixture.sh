#!/usr/bin/env bash
set -euo pipefail

if (( $# != 0 )); then
  printf 'Usage: bash engine/scripts/codex-fixture.sh\n' >&2
  exit 2
fi

engine_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
engine="$engine_dir/target/debug/cos-engine"
if [[ ! -x "$engine" ]]; then
  printf 'Build cos-engine first: cargo build --locked --manifest-path engine/Cargo.toml --target-dir engine/target\n' >&2
  exit 1
fi
if ! command -v codex >/dev/null; then
  printf 'The Codex CLI must be installed and on PATH.\n' >&2
  exit 1
fi

temp_root="$(cd -- "${TMPDIR:-/tmp}" && pwd -P)"
session="$(mktemp -d "$temp_root/cognitive-os-codex.XXXXXXXX")"
trap 'rm -rf -- "$session"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -- "$session/brain" "$session/codex" "$session/work"

"$engine" --home "$session/brain" init --vaults "$engine_dir/fixtures/vault" --embedder mock:hash >&2
"$engine" --home "$session/brain" cycle --json >&2
(
  cd -- "$session/work"
  export CODEX_HOME="$session/codex"
  codex mcp add cognitive_os_fixture -- "$engine" --home "$session/brain" serve >&2
  codex mcp get cognitive_os_fixture >&2
)

trap - EXIT
printf '%s\n' "$session"
