# Read-only memory tools for MCP clients

This adapter connects a compatible local MCP harness to the existing engine. It
uses stdio, not an HTTP port, and exposes only `search` and `health`. It does not
run ingestion, initialize databases, write notes, or install itself into a harness.
The broader M2 milestone (events, `get_page`, supervision) is not complete.

## Build and prepare a fixture

Rust 1.89 or newer is required. From the repository root:

```bash
cargo build --locked --manifest-path engine/Cargo.toml
COS_HOME=$(mktemp -d)
engine/target/debug/cos-engine --home "$COS_HOME" init --vaults engine/fixtures/vault --embedder mock:hash
engine/target/debug/cos-engine --home "$COS_HOME" cycle --json
engine/target/debug/cos-engine --home "$COS_HOME" serve
```

The last command waits for an MCP client on stdin/stdout. It is not an interactive
shell. `mock:hash` is for deterministic testing, not useful semantic embeddings.
For a real brain, initialize and cycle it separately with the intended embedder.

## Harness connection

Configure your MCP client's stdio server entry with this process specification,
replacing both absolute paths. The surrounding configuration key differs between
clients; this is a process entry, not an automatically installed configuration.

```json
{
  "command": "/absolute/path/cognitive-os/engine/target/debug/cos-engine",
  "args": ["--home", "/absolute/path/selected-brain-home", "serve"]
}
```

`--home` is mandatory for `serve`, even when `COS_HOME` is set. A missing config
or database fails at startup without creating one. Each process is fixed to one
home loaded at startup; tools cannot select another home or vault. Restart the
process after changing its configuration.

Connecting a client gives that client access to the selected brain's indexed
snippets and cycle metadata. Use a dedicated home for each scope and do not
connect a private brain to an untrusted client. Search sends the query to the
embedder URL already configured in that home (unless using the mock embedder).
No credentials, client configuration, or real vaults are changed by the example.
Sanitization protects new errors; it does not rewrite historical cycle records.

## Tools and errors

1. `search`: `{ "query": "silent failure", "k": 8 }`. Query is limited to
   4096 bytes; `k` must be 1–100. Unknown fields are rejected. Results use the
   same search function as the CLI, including its FTS5 query syntax, hybrid
   rankings and timing metadata.
2. `health`: no arguments. Returns the latest recorded cycle. `null` means no
   cycle has been recorded, not that the brain is healthy. A historical green
   cycle is not a live embedder probe.

Brain names must be a single path component, never absolute or traversal paths.
Both tools open the existing SQLite database read-only. WAL-mode databases need
usable `-wal`/`-shm` sidecars or a writable containing directory so SQLite can
maintain its coordination files. Read-only SQL does not mean an entirely
read-only filesystem. Startup probes the database and reports this prerequisite
if access fails. Do not mark a live database immutable or copy only its main
file while another process is writing. Search uses one consistent read snapshot;
health reads only the newest cycle instead of materializing the full ledger. Tool failures set MCP
`isError`; sanitized diagnostics go to stderr, never the protocol stdout. Invalid
arguments and unknown tools are explicitly rejected rather than treated as empty
successful results. Blocking queries run outside
the async protocol loop. There is one active search and one independent health
lane; additional calls are rejected immediately with a retryable busy error,
not queued. Search has a five-second budget across database work and embedding
requests; MCP responses have a six-second deadline. Legacy embedding fallback
is attempted only for unsupported API responses (404/405), not timeouts.

Timed-out blocking workers retain their lane until they actually finish, so
retries cannot accumulate orphan workers. System DNS resolution in the existing
synchronous HTTP client is not cancellable; a stalled resolver can keep the
search lane busy beyond the response deadline. Health remains independently
available. Restart the server if a resolver never returns. The engine still scans
all stored vectors, so this adapter does not claim large-corpus performance.

## Verification and dependency choice

```bash
cargo test --locked --manifest-path engine/Cargo.toml
```

The integration suite launches the actual binary with the official SDK client,
performs initialization and tool calls, compares responses with CLI results,
and uses only temporary fixture state. It does not connect a real AI account.

Selected [rmcp 3.10.0](https://github.com/modelcontextprotocol/rust-sdk/releases/tag/rmcp-v3.10.0),
the official Apache-2.0 Rust MCP SDK, released 12 November 2026. Tokio supplies
the async runtime. A hand-written JSON-RPC server was rejected because it would
duplicate protocol negotiation, framing, dispatch and lifecycle handling.
An HTTP server was rejected because no remote access or authentication surface
is required for local harness integration. The engine remains MIT-licensed;
its dependencies retain their respective licences.
