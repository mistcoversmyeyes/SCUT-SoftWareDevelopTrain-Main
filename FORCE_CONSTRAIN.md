# 强制约束

本文件优先级高于其他项目文档。若与实现便利冲突，先修改计划，不要绕开约束。

## 生命周期

- 本项目采用计划驱动的阶段式迭代增量模型，不采用敏捷/Scrum 模型。
- 每周开发统一称为周迭代、iteration 或阶段迭代；禁止称为 sprint。
- 禁止把每周产物默认描述为 MVP 或 `MVP+`；只有规格明确证明核心业务闭环成立时才可这样命名。
- 产品背景第一事实源是 `res/WMS仓储管理系统--产品介绍资料.pdf`。
- 不确定的业务语义必须进入 `docs/specs/` 或 product debt，禁止凭空实现。

## Git 工作流

- `main` 是稳定验收基线，只接收通过验证的阶段成果或文档基线。
- `dev/iterN` 是第 N 周迭代集成分支。
- 具体实现使用 `feature/weekN-<short-topic>`，从对应 `dev/iterN` 派生并回合到该迭代分支。
- 文档 harness 或纯文档改动使用 `harness/<topic>` 或 `docs/<topic>`。
- hotfix 只用于修复已进入稳定基线的阻断问题，命名为 `hotfix/<short-topic>`。
- 多 agent 并行时必须使用独立 worktree，默认放在 `.worktrees/`，不得共享同一未提交工作区。
- 不得重置、覆盖或清理与当前任务无关的用户改动。

## 提交与验证

- 提交必须原子化：一个提交只表达一个可审查的行为变化或文档变化。
- 提交信息使用 `<type>(optional-scope): <summary>`；允许中文 summary。
- 修改后端行为前后至少运行 `mvn test`，除非明确说明无法运行。
- 修改前端行为前后至少运行 `npm test`；影响构建产物或路由时同时运行 `npm run build`。
- 任何无法运行的验证必须在最终交付中说明原因和残余风险。

## 文档产物路径(强制覆盖 superpowers 文档路径)

- 需求、设计和工作包规格写入 `docs/specs/`。
- 执行计划写入 `docs/exec-plans/active/`，完成后移入 `docs/exec-plans/completed/`。
- 技术债写入 `docs/exec-plans/tech-debt-tracker.md`。
- 产品语义、流程、角色、字段口径未定的问题写入 `docs/exec-plans/product-debt-tracker.md`。
- 外部资料索引写入 `docs/references/`；不要记录密钥、真实私有地址、token、个人账号。

## 同步维护

- 改动模块结构、运行方式、分支流程或文档产物路径时，必须同步更新 `AGENTS.md` 与对应 companion 文档。
- 可机械化但尚未硬化的规则记录在 `docs/constraints/WORKFLOW.md` 或 `docs/constraints/BRANCHING.md` 的 TODO(harden) 区域。
