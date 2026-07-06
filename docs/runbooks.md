# Runbooks

## Overview

Cognitive OS is a specification repository, not a deployed service. There are no production incidents to respond to. However, the following procedures apply to repository maintenance.

## Repository Maintenance Runbook

### Updating the Master Specification (AGENTS.md)

1. Create a branch: `git checkout -b docs/update-agents`
2. Edit `AGENTS.md` following the living document principle
3. Update all harness adapters if core directives changed
4. Run `npm run validate` to ensure consistency
5. Open PR with description of what was learned/changed
6. Squash merge to `main` - release workflow triggers automatically

### Adding a New Harness Adapter

1. Create the adapter file (e.g., `NEWTOOL.md`)
2. Reference master spec: `> Full spec: /home/ae/AE/AGENTS.md`
3. Include active directives, merge CLI, package manager detection
4. Add to harness table in README.md
5. Add to CI `docs-validation` job
6. Add to `.github/CODEOWNERS`

### Broken CI Pipeline

1. Check which job failed: `gh run list --limit 5`
2. View logs: `gh run view <run-id> --log-failed`
3. Common fixes:
   - Markdown lint failures: `npm run lint:fix`
   - Format failures: `npm run format`
   - Test failures: Read the test output, fix the spec file

### Rollback a Bad Commit

```bash
git revert <commit-hash>
git push origin main
```

## External References

- [Cognitive OS Repository](https://github.com/aegntic/cognitive-os)
- [Factory AI Documentation](https://docs.factory.ai)
- [Claude Code Skills](https://code.claude.com/docs/en/skills)
