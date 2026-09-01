#!/usr/bin/env bash
# Recreates the generated hygiene violations for the broken-skill fixture:
# a >200KB blob and a node_modules dir. qc.py must fail on both; this script
# exists so the fixture can be regenerated after a clean checkout without
# committing junk (binaries / node_modules) to the repo.
set -euo pipefail
cd "$(dirname "$0")"
python3 -c "open('blob.bin','wb').write(b'x'*(210*1024))"
mkdir -p node_modules/fake
echo '{"dep":true}' > node_modules/fake/package.json
echo "fixture hygiene artifacts recreated: blob.bin (215040 bytes), node_modules/fake/"
