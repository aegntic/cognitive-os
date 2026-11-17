# The Silent Failure Incident

A PGlite-backed brain grew to 7.1 GB, exceeded an in-memory WASM heap, and every
import failed for 2.5 months — 4,474 logged failures, zero successful imports —
while the service reported green skip-cycles.

This engine treats silent no-op success as a bug class of its own.
Every cycle ends at a gate block. A failed gate fails the cycle, exits non-zero,
and writes a JSONL health record naming the gate.
