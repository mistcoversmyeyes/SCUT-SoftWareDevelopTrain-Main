# Commit

本文件是提交规则事实源；分支命名、worktree 和合并方向见 `docs/constraints/BRANCHING.md`，变更验证门禁见 `docs/constraints/WORKFLOW.md`。

- 提交必须原子化：一个提交只表达一个可审查的行为变化或文档变化。
- 提交信息使用 `<type>(optional-scope): <summary>`；允许中文 summary。
- 推荐类型：`feat`、`fix`、`docs`、`test`、`refactor`、`chore`。
- 不混合生成依赖 churn、格式化和行为变化，除非任务明确要求。
- 提交前按 `WORKFLOW.md` 的 Verification Gates 运行相关验证。
- 任何无法运行的验证必须在最终交付中说明原因和残余风险。
