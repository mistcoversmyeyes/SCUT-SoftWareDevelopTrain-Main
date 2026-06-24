# Grill: specs naming
Date: 2026-06-24

## Intent
统一 `docs/specs/` 下规格文档的文件命名，减少当前 `weekN`、`module-*`、无日期文件名混用造成的检索和维护混乱。

## Constraints
- 文件名统一带日期前缀。
- 日期取规格内容最初创建日期，而不是本次重命名日期或当前路径恢复日期。
- 文件名不包含 `weekN`，因为时间戳已经提供排序信息，周次归属应由 specs/iterations 索引承担。
- 本次只处理文件名、引用路径和命名规则，不重写正文标题里的历史语境。

## Key decisions
- Decision: 使用 `YYYY-MM-DD-<business-topic>-<doc-kind>.md`。Reason: 保留 superpowers 原有时间戳习惯，同时用业务主题表达内容。Alternative considered: `YYYY-MM-DD-weekN-<topic>-<type>.md`，因 `weekN` 提供零增量信息而拒绝。
- Decision: 对迁移/恢复过的规格按内容首次出现日期命名。Reason: 文件名表达规格产生时间，而不是文档整理时间。Alternative considered: 统一使用 `2026-06-24` 或当前路径首次添加日期，因会抹掉历史语义而拒绝。

## Surfaced assumptions
- `docs/specs/index.md` 和 `docs/iterations/index.md` 应承担周次归属说明，不应把周次塞进每个规格文件名。
- `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` 是原始 superpowers 默认，但本项目硬约束已将规格路径覆盖到 `docs/specs/`。

## Out of scope
- 不重写规格正文标题。
- 不重构 specs 文档内容。
- 不改变执行计划、测试文档或代码行为。
