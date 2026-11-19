---
paths:
  - "**"
---

# Workflow

- Never commit keys or secrets.
- Branch from `origin/develop`; never commit to `main` or `develop`. Commit with the `git-commit` skill and open PRs with the `write-pr` skill. No AI co-author trailer.
- Open an issue before a contract change and record the decision there.
- A new required environment variable must be on the server before the change reaches `main`; every push to `main` deploys.
