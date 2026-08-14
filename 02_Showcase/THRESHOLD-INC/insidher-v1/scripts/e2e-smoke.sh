#!/usr/bin/env bash
# ponytail: minimal API e2e against local wrangler :8788 — not a full Android instrumented suite
set -euo pipefail
BASE="${BASE_URL:-http://127.0.0.1:8788}"

echo "== health =="
curl -sf "$BASE/health" | head -c 200
echo

# Full signed ECDSA flow needs device key; this smoke only checks public surfaces.
echo "== sms webhook (unsigned) =="
code=$(curl -s -o /tmp/insidher-sms.json -w '%{http_code}' -X POST "$BASE/webhook/sms" \
  -H 'Content-Type: application/json' \
  -d '{"from":"+61400000001","body":"hi want to book thursday","messageId":"e2e-smoke-1"}')
echo "status=$code body=$(head -c 180 /tmp/insidher-sms.json)"
test "$code" = "202" -o "$code" = "200" -o "$code" = "400" && echo "sms path reachable"

echo "e2e smoke done (signed device flows covered by vitest + android unit tests)"
