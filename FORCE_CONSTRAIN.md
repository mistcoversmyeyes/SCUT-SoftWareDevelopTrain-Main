# 强制约束

本文件优先级高于其他项目文档。若与实现便利冲突，先修改计划，不要绕开约束。

## 生命周期

- 本项目采用计划驱动的阶段式迭代增量模型，不采用敏捷/Scrum 模型。
- 每周开发统一称为周迭代、iteration 或阶段迭代；禁止称为 sprint。
- 禁止把每周产物默认描述为 MVP 或 `MVP+`；只有规格明确证明核心业务闭环成立时才可这样命名。
- 产品背景第一事实源是 `docs/references/Course PPT/WMS仓储管理系统--产品介绍资料.pdf`。
- 不确定的业务语义必须进入 `docs/specs/` 或 product debt，禁止凭空实现。

## 文档产物路径(强制覆盖 superpowers 文档路径)

- 需求、设计和工作包规格写入 `docs/specs/`。
- 执行计划写入 `docs/exec-plans/active/`，完成后移入 `docs/exec-plans/completed/`。
- 技术债写入 `docs/exec-plans/tech-debt-tracker.md`。
- 产品语义、流程、角色、字段口径未定的问题写入 `docs/exec-plans/product-debt-tracker.md`。
- 外部资料索引写入 `docs/references/`；不要记录密钥、真实私有地址、token、个人账号。

## Git 工作流
- Git 分支层级、命名、合并流程和 worktree 规则见 `docs/constraints/BRANCHING.md`。
- 具体工作流规则见 `docs/constraints/WORKFLOW.md`。
- 提交粒度、提交信息和提交前验证规则见 `docs/constraints/COMMIT.md`。

## 同步维护

- 改动模块结构、运行方式、分支流程或文档产物路径时，必须同步更新 `AGENTS.md` 与对应 companion 文档。
- `docs/` 下新增、移动、删除文档时，必须同步更新最近的 `index.md`。
- 可机械化但尚未硬化的规则记录在 `docs/constraints/WORKFLOW.md` 或 `docs/constraints/BRANCHING.md` 的 TODO(harden) 区域。
