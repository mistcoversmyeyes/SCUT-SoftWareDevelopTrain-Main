# Grill: docs index sync
Date: 2026-06-22

## Intent
同步 `docs/` 下已有 `index.md` 与当前文档结构，并在 `FORCE_CONSTRAIN.md` 中加入简短的 index 同步硬规则，减少后续文档漂移。

## Constraints
- 修改前必须遵守 `FORCE_CONSTRAIN.md`。
- 保留现有文档 harness 方向：`AGENTS.md` 做地图，深层约束放 companion 文档。
- 只同步已有 index，不主动为没有 index 的目录新建索引。
- 新规则必须简短，放在 `FORCE_CONSTRAIN.md` 的同步维护区域。

## Key decisions
- Decision: 以实际 `docs/` 文件树为准同步 `specs`、`iterations`、`references` 三个已有索引。Reason: 这些目录已有 index 且当前内容与文件现状不一致或需要确认。Alternative considered: 为 `constraints`、`exec-plans`、`operations` 一并创建 index，因用户只点名已有 index 同步而暂不扩展范围。
- Decision: `FORCE_CONSTRAIN.md` 只追加最近 index 同步规则。Reason: 规则属于跨目录行为约束，且不适合只落在某个 companion 文件。Alternative considered: 写入 `WORKFLOW.md`，但该规则覆盖所有 `docs/` companion 文档变更。

## Surfaced assumptions
- “部分文件夹的 index.md” 指当前已有的 `docs/specs/index.md`、`docs/iterations/index.md`、`docs/references/index.md`。
- `docs/references/index.md` 的清单与当前文件树基本一致，仍可微调措辞以强调它只索引本地外部资料。

## Out of scope
- 不创建新的目录索引。
- 不重构 AGENTS.md 地图。
- 不移动或重命名现有文档。
