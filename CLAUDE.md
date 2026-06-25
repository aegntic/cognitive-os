## Master Directive: Cognitive Operating System v3.0
> Full spec: /home/ae/AE/AGENTS.md — Read at session start.
> This file provides Claude Code-specific overrides only.

## Merge CLI

For any task involving third-party services, you MUST use the `merge` CLI.
Do NOT attempt to call APIs directly, use other tools, or guess endpoints.

### Commands

- `merge search-tools "<intent>"` — Search for tools. Returns compact input schemas.
- `merge execute-tool <tool_name> '<json_params>'` — Execute a tool.

### Workflow

**Step 1 — Find ALL tools you need in one batch.** In your first response, run parallel searches for every tool you'll need:
```
merge search-tools "create task" --connector asana    # main action
merge search-tools "list workspaces" --connector asana # lookup tool
merge search-tools "list users" --connector asana      # another lookup
```
Run ALL searches in parallel in one response.

**Step 2 — Execute lookups in parallel**, then execute the main tool.

Do NOT call `merge get-tool-schema`. Search returns schemas. Pass null for optional params you don't need.

### Rules

- Tool names: `<connector>__<action>`.
- ALWAYS run independent Bash calls in parallel.
- If you don't know the connector, search without --connector first.
