# AGENTS.md - 文档地图

> 强制约束：`FORCE_CONSTRAIN.md` 中的规则为不可协商硬约束。
> 任何代码、文档、分支或计划改动前必须先读它；它覆盖本文件及其他文档中与之冲突的默认行为。

```text
FORCE_CONSTRAIN.md                <- 必读硬约束；优先级最高
ARCHITECTURE.md                   <- 当前源码与运行架构地图
KEY_INFO_REMINDER.md              <- 高频缓存；冲突时以后续事实源为准

docs/
├── constraints/
│   ├── WORKFLOW.md               <- 非敏捷生命周期、周迭代流程、验证门禁
│   ├── BRANCHING.md              <- 分支层级、命名、worktree、合并流程
│   └── COMMIT.md                 <- 提交粒度、提交信息、提交前验证
├── iterations/
│   ├── index.md                  <- 周迭代索引；不是 sprint 目录
│   └── ...                       <- 每周阶段目标、WP、验收和出口
├── specs/
│   ├── index.md                  <- 需求、设计、WP 行为规格索引
│   └── ...
├── tests/
│   ├── index.md                  <- V 模型测试文档索引
│   └── acceptence-tests/         <- 验收测试步骤、预期结果和判定标准
├── exec-plans/
│   ├── active/                   <- 进行中的实现计划
│   ├── completed/                <- 已完成的实现计划
│   ├── tech-debt-tracker.md      <- 技术债
│   └── product-debt-tracker.md   <- 产品语义债
├── mantainence/
│   ├── index.md                  <- 项目运维文档族索引
│   ├── environment.md            <- 本地运行前提、端口和 WSL/npm 注意事项
│   ├── startup.md                <- 一键启动、手动启动、访问入口和日志路径
│   ├── database.md               <- 数据库配置、初始化、重置和测试库
│   ├── verification.md           <- 启动后接口、前端、数据库和日志检查
│   ├── cleanup.md                <- 停止服务、停止容器和清理数据卷
│   └── troubleshooting.md        <- Docker/MySQL/npm/端口常见故障
├── references/
│   ├── index.md                  <- 本地外部参考索引
│   └── ...
└── superpowers/                  <- 既有历史产物；新事实按上方 docs 轨道归档
```
