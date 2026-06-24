# Grill: mantainence doc split
Date: 2026-06-23

## Intent

将 `docs/mantainence/startup-guide.md` 拆成多个面向 Agent 的项目运维子文档，使多个 Agent 能按任务阶段快速定位启动、数据库、验证、清理和故障排查信息。

## Constraints

- `docs/mantainence/` 是用户指定的项目运维文档族目录名。
- `docs/mantainence/startup-guide.md` 不保留，拆分后彻底删除。
- 外部入口统一收敛到 `docs/mantainence/index.md`，避免 README 和缓存文档随子文档继续拆分而漂移。
- 新增、移动、删除 `docs/` 文档时必须同步最近的 `index.md`，并同步 `AGENTS.md` 等入口地图。

## Key decisions

- Decision: 拆出独立 `verification.md`。Reason: 启动和验证是 Agent 执行中的两个阶段，拆开便于后续脚本化健康检查。Alternative considered: 将验证内容留在 `startup.md` 末尾。
- Decision: `startup-guide.md` 删除而不是降级为 router。Reason: `index.md` 已承担文档族入口职责，保留双入口会增加漂移风险。Alternative considered: 保留短 router 文件。
- Decision: README 和 `KEY_INFO_REMINDER.md` 只指向 `docs/mantainence/index.md`。Reason: 外部入口稳定，具体文档由目录索引路由。Alternative considered: 直接指向 `startup.md` 或其他子文档。

## Surfaced assumptions

- 运维文档的主要读者是仓库中同时工作的多个 Agent，其次才是人类快速查命令。
- 运维文档回答“如何把系统跑起来并确认可用”，不承载周迭代验收记录或需求范围。

## Out of scope

- 不在本轮添加运维脚本、Git hooks 或 CI。
- 不把 Week 4 验收结论、工作包进度和截图记录迁入 `docs/mantainence/`。
