# Branching

## Branch Roles

```text
main
  stable acceptance baseline

dev/iterN
  weekly iteration integration branch

feature/weekN-<short-topic>
  implementation branch for one work package

harness/<topic> or docs/<topic>
  documentation and harness-only changes

hotfix/<short-topic>
  urgent fix against a stable baseline
```

## Current Observed Branches

- `main` / `origin/main`
- `dev/iter1` / `origin/dev/iter1`
- `feature/week1-wms-login-tabs`
- `harness/docs`

These observed branches are treated as the initial convention. Future branch names should preserve the same hierarchy unless this document is deliberately updated.

## Merge Direction

- Feature work starts from the active `dev/iterN`.
- Feature branches merge back into the same `dev/iterN`.
- `dev/iterN` merges to `main` only after the iteration acceptance gate passes.
- `main` must not be used as a scratch branch.
- Documentation-only harness work can merge into the active integration branch after review, then into `main` at the next stable checkpoint.

## Worktree Rules

- 主工作树用于 `main` / `dev/iterN` 的集成、验收和文档基线维护。
- topic、feature、hotfix 分支默认在 `.worktrees/` 下创建和开发。
- Multi-agent 或并行工作必须使用独立 worktree。
- One worktree owns one branch and one bounded task.
- Agents must not clean, reset, or reformat files outside their task scope.
- Before merging parallel work, inspect `git status` and task-owned paths.

Example:

```bash
git worktree add .worktrees/week2-inbound -b feature/week2-inbound dev/iter2
```

## Commit Rules

- Format: `<type>(optional-scope): <summary>`.
- Recommended types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`.
- Keep commits atomic and reviewable.
- Do not mix generated dependency churn, formatting, and behavior changes unless the task explicitly requires it.

## Protection Intent

- `main` should be protected from direct commits.
- `dev/iterN` should require at least the relevant local verification before merge.
- Feature branches should be short-lived and tied to a specific week/work package.

## Harden Candidates

- TODO(harden): branch name hook for `dev/iterN`, `feature/weekN-*`, `harness/*`, `docs/*`, `hotfix/*`.
- TODO(harden): commit-msg hook for conventional commit prefix.
- TODO(harden): pre-push hook or CI check for relevant test commands.
