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

## Branch State

- 本文件不缓存当前分支；需要时运行 `git branch --show-current`。
- 已见分支族包括 `main`、`dev/iterN`、`feature/weekN-*`、`harness/*`、`docs/*`、`refactor/*`。
- 后续分支名应保持上述层级，除非本文件被显式更新。

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

提交规则事实源见 `docs/constraints/COMMIT.md`；本文件只维护分支、worktree 和合并方向。

## Protection Intent

- `main` should be protected from direct commits.
- `dev/iterN` should require at least the relevant local verification before merge.
- Feature branches should be short-lived and tied to a specific week/work package.

## Harden Candidates

- TODO(harden): branch name hook for `dev/iterN`, `feature/weekN-*`, `harness/*`, `docs/*`, `hotfix/*`.
- TODO(harden): commit-msg hook for conventional commit prefix.
- TODO(harden): pre-push hook or CI check for relevant test commands.
