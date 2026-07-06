# Contributing to Cognitive OS

## Development Setup

```bash
git clone https://github.com/aegntic/cognitive-os.git
cd cognitive-os
npm install   # Installs deps + pre-commit hooks via husky
```

## Naming Conventions

All files in this repository follow consistent naming patterns:

1. **Markdown files**: `UPPERCASE.md` for top-level specs (AGENTS.md, README.md, CONTRIBUTING.md), `kebab-case.md` for docs and templates
2. **Directories**: `kebab-case` (e.g., `.github/ISSUE_TEMPLATE/`)
3. **Config files**: dotfile prefix (e.g., `.markdownlint.json`, `.prettierrc`)
4. **GitHub Actions**: `kebab-case.yml` in `.github/workflows/`
5. **Skills**: `{skill-name}/SKILL.md` inside `.factory/skills/`

## Markdown Standards

- Use ATX-style headers (`# Header`)
- Maximum line length: 120 characters
- No trailing whitespace
- Lists must have blank lines before and after
- Code blocks must specify language

## Testing

Markdown validation tests live in `tests/` and validate:

1. All `.md` files pass markdownlint
2. All internal links resolve
3. All spec files meet minimum content requirements
4. Harness adapters reference the master AGENTS.md

Run tests: `npm test`

## Git Workflow

1. Create a branch: `git checkout -b feat/your-feature`
2. Make changes, ensure pre-commit hooks pass
3. Run full validation: `npm run validate`
4. Open a PR with the template filled out
5. Squash merge to `main`

### Commit Format

```
<type>: <description>
```

Types: `feat`, `fix`, `docs`, `test`, `chore`, `perf`, `ci`, `refactor`

## Pre-commit Hooks

Husky runs automatically on commit:

1. `lint-staged` - Lints and formats staged files
2. Prevents commits with linting errors

## Code Quality

- All markdown must pass `markdownlint`
- All files must be formatted with `Prettier`
- No duplicate content blocks across harness adapters
- AGENTS.md is the source of truth, adapters are subsets
