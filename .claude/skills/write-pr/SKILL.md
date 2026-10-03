---
name: write-pr
description: Generate PR title, body, and labels from commits since the base branch, then create the PR on GitHub. Handles base branch detection, label selection, and PR creation end-to-end.
allowed-tools: Bash(git *:*), Bash(bash *create-pr.sh:*), Bash(cat *:*), Read, Write
---

## Step 1 — Gather Context

```bash
git branch --show-current
git fetch origin develop --quiet 2>/dev/null || true
git log origin/develop..HEAD --oneline 2>/dev/null || git log --oneline -15
git diff origin/develop...HEAD --stat 2>/dev/null || git diff HEAD~5...HEAD --stat
git diff origin/develop...HEAD 2>/dev/null || git diff HEAD~5...HEAD
```

Read the PR template if the repository has one — it does not always exist, so do not let a missing
file stop the run:

```bash
cat .github/PULL_REQUEST_TEMPLATE.md 2>/dev/null || echo "(no template)"
```

## Step 2 — Determine Labels

Read `${CLAUDE_SKILL_DIR}/references/labels.md` and select 1–2 appropriate labels based on the nature of the changes.
Read `${CLAUDE_SKILL_DIR}/references/commit-conventions.md` for commit type and scope naming rules.

## Step 3 — Generate PR Content

**Title** — the format depends on the base branch:

- Base is `develop` (or any non-`main` base): generate 3 options in the format `[scope] description`.
  - Scope: the domain the changed files belong to — `attendance` or `qr`. Lowercase, wrapped in brackets: `[attendance]`, `[qr]`. Use `[global]` for changes outside any domain (config, tooling, agent/skill docs) and `[ci/cd]` for pipeline work.
  - Description: Korean, concise, no emojis, max 50 characters total
  - Wrap class names, method names, annotations, file names, and technical terms in backticks (e.g., `@Transactional`, `AdminService`, `SKILL.md`)
- Base is `main`: this is a release PR. Title is a single bare `vX.Y.Z` (no scope brackets, no description) — see `${CLAUDE_SKILL_DIR}/references/commit-conventions.md` for how to pick X/Y/Z from the commits going in, and bump `version` in `build.gradle.kts` to match in the same PR.

**Body** — Follow `.github/PULL_REQUEST_TEMPLATE.md` when it exists. When it does not, use:
`## 개요` (one or two sentences), `## 변경 사항` (bullet per change), `## 확인` (how it was verified),
and a closing `Closes #<issue>` line when an issue exists.

- Korean 합쇼체: `~하였습니다`, `~되었습니다`, `~추가하였습니다`
- No emojis
- Max 2500 characters
- Wrap all proper nouns and technical identifiers in backticks: class names, method names, annotations, file names, field names, config keys, module names, and agent names.

## Step 4 — Write Body & Show Preview

Write the body to `PR_BODY.md` **in your scratchpad directory**, not the repository root — an untracked
file there can be swept into a later `git add`. Then display:

```
## PR 제목 후보
1. [title1]
2. [title2]
3. [title3]

## 선택된 라벨
- label1, label2

## PR 본문 미리보기
[body content]
```

Use AskUserQuestion to ask the user which title to use (present options 1/2/3). Wait for the answer before proceeding.

## Step 5 — Create PR

Run the creation script with the confirmed title and labels:

```bash
bash "${CLAUDE_SKILL_DIR}/scripts/create-pr.sh" "<confirmed-title>" "<scratchpad>/PR_BODY.md" "<label1>,<label2>"
```

The script picks the base itself (feature branch → `develop`, `develop` → `main`), refuses to run on
`main`, and fails with a clear message if the base branch does not exist on `origin`.

For a release PR to `main`, the title (`vX.Y.Z`) must still pass unquoted — the CI `pr-title` check
special-cases exactly that pattern when the PR base is `main`.

Label names contain emoji and spaces — pass them verbatim. A mismatched name fails PR creation.

After creation, display the PR URL.
