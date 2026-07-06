# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this repository:

1. **Do NOT open a public issue.**
2. Email security concerns to the repository owner via GitHub's private vulnerability reporting.
3. Use: `gh security-advisory create` or the "Report a vulnerability" button under the Security tab.
4. Include: description, reproduction steps, potential impact.

You will receive a response within 48 hours.

## Security Measures in Place

| Measure           | Status                                                         |
| ----------------- | -------------------------------------------------------------- |
| Branch protection | Enabled on `main` (enforce admins, no force push, no deletion) |
| Secret scanning   | Enabled with push protection                                   |
| Dependency review | Dependabot weekly updates + vulnerability alerts               |
| Pre-commit hooks  | Husky + lint-staged (prevents secrets in staged files)         |
| Code review       | CODEOWNERS enforced on all paths                               |

## Secret Handling

- No secrets, API keys, or credentials are stored in this repository.
- Environment variables are documented in `.env.example` (all values are placeholders).
- Pre-commit hooks and CI tests scan all tracked files for secret patterns.
- GitHub secret scanning with push protection is active.
