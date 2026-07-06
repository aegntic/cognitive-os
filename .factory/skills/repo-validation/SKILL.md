---
name: repo-validation
description: Validates that all specification files in the Cognitive OS repo pass markdownlint, are properly formatted, contain required sections, and maintain consistency between master spec and harness adapters.
---

# Repository Validation Skill

This skill validates the Cognitive OS specification repository for:

1. **Markdown linting** - All `.md` files pass markdownlint
2. **Formatting** - All files formatted with Prettier
3. **Link integrity** - All internal markdown links resolve
4. **Content completeness** - AGENTS.md has all 16 sections
5. **Adapter consistency** - Harness adapters reference the master spec
6. **File size limits** - No file exceeds 50KB
7. **No secrets** - No hardcoded credentials in any file

## Usage

```bash
npm run validate
```

This runs lint + format check + test in sequence.
