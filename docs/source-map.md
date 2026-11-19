# Cognitive OS source map

Use this repository as the source of truth for the Cognitive OS specification,
harness setup, memory engine, and memory protocol. Keep deployment state and
durable execution separate from the memory interface.

| Component                          | Source of truth                                                                                                                     | Boundary                                                                                                                                          |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Specification and harness adapters | [Root repository](../README.md) and [AGENTS.md](../AGENTS.md)                                                                       | Edit the specification here rather than maintaining copies in consumers.                                                                          |
| Memory engine and fixtures         | [`engine/`](../engine/README.md)                                                                                                    | Rust CLI, index, tests and [read-only MCP interface](../engine/MCP.md). Do not duplicate the implementation in a consumer.                        |
| Original standalone engine         | [`aegntic/cognitive-os-engine`](https://github.com/aegntic/cognitive-os-engine)                                                     | Historical upstream; the import preserved its Git lineage and MIT licence. New fixes should target this repository's `engine/`.                   |
| Durable execution and approvals    | [`aegntic/aegntic-os-v2`](https://github.com/aegntic/aegntic-os-v2)                                                                 | Separate Bun/Pi Durable application. Consume the documented memory interface instead of copying the engine or moving the whole workspace here.    |
| Public product pages               | [AEGNTIC website source](https://github.com/aegntic/aegnticdotai) and [Cognitive OS product page](https://aegntic.ai/cognitive-os/) | The site explains the product. Implementation and current capability status belong here, not in a second copy of the specification.               |
| Hosted evidence demo               | Separate, access-controlled deployment                                                                                              | Do not copy hosted settings, credentials, saved briefs, review state or private assets into public Git. Only share a reviewed, synthetic example. |

## Prologue is a separate memory service

[Prologue](https://github.com/aegntic/prologue) is a TypeScript memory service.
Its [MemoryMatrix tools](https://github.com/aegntic/prologue/blob/master/src/mcp/tools.ts)
can store, query, promote and delete records. The Rust MCP interface in this
repository exposes read-only search and health over indexed Markdown. It is not
a drop-in replacement. Before proposing a migration, identify installed harness
consumers and check which operations they use. Do not change an existing harness
or move its data as part of this consolidation.

## What already moved

- [PR #37](https://github.com/aegntic/cognitive-os/pull/37) imported the engine
  with history and its MIT licence. The standalone
  [atomic indexing patch](https://github.com/aegntic/cognitive-os-engine/pull/2)
  is already included, even if that upstream PR still appears open.
- [PR #39](https://github.com/aegntic/cognitive-os/pull/39) added the read-only
  stdio MCP adapter, [PR #40](https://github.com/aegntic/cognitive-os/pull/40)
  switched to Rustls, and
  [PR #41](https://github.com/aegntic/cognitive-os/pull/41) hardened the adapter.

The root [licence](../LICENSE) and the [engine's MIT licence](../engine/LICENSE)
are distinct. Keep those boundaries when copying or packaging code.

## Integration order

1. Use the [isolated Codex fixture](../engine/MCP.md#try-it-with-codex-using-only-repository-fixtures)
   to check local read-only memory access. This does not enroll existing
   harnesses or vaults.
2. Have one consumer use that same interface and verify a real recall result
   with source evidence. Keep execution, approvals and persistence with the
   consumer; keep indexing and retrieval with the engine.
3. Point public documentation to the canonical sources. Reconcile the
   access-controlled demo separately against its current deployed source,
   not against an older local copy. Leave existing deployments and original
   repositories available for rollback.

A repository import, a passing fixture, and a consumer reading live memory
are separate milestones. Do not describe one as proof of the others.
