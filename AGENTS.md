# AGENTS.md - 仓库地图

> 强制约束：`FORCE_CONSTRAIN.md` 中的规则为不可协商硬约束。
> 任何代码、文档、分支或计划改动前必须先读它；它覆盖本文件及其他文档中与之冲突的默认行为。

```text
AGENTS.md                         <- 本文件，仓库导航入口，不做百科
FORCE_CONSTRAIN.md                <- 必读硬约束：生命周期、Git、文档产物路径
ARCHITECTURE.md                   <- 架构地图：产品背景、源码目录、依赖方向、外部边界
KEY_INFO_REMINDER.md              <- 高频事实：运行命令、日志、演示账号、背景资料

backend/                          <- Spring Boot 后端
  pom.xml
  src/main/java/com/scut/wms/
    WmsApplication.java
    auth/                         <- 登录与演示 token
    config/                       <- CORS、全局异常处理
  src/test/java/com/scut/wms/

frontend/                         <- Vue 3 + Vite + Element Plus 前端
  package.json
  vite.config.js
  src/
    api/                          <- axios 与认证 API
    components/                   <- 菜单、标签栏等组件
    router/                       <- Vue Router 与登录守卫
    stores/                       <- Pinia 状态
    views/                        <- 登录页、主布局、WMS 占位页
    menu.js                       <- 菜单与 WMS 模块元数据

res/                              <- 课程与产品资料
  WMS仓储管理系统--产品介绍资料.pdf <- WMS 背景第一事实源

scripts/                          <- 本地脚本
  start.sh                        <- 同时启动后端与前端

docs/
├── constraints/
│   ├── WORKFLOW.md               <- 非敏捷生命周期、周迭代流程、验证门禁
│   └── BRANCHING.md              <- 分支层级、命名、worktree、合并流程
├── iterations/
│   ├── index.md                  <- 周迭代索引；不是 sprint 目录
│   └── ...                       <- 每周阶段目标、WP、验收和出口
├── specs/
│   ├── index.md                  <- 需求、设计、WP 行为规格索引
│   └── ...
├── exec-plans/
│   ├── active/                   <- 进行中的实现计划
│   ├── completed/                <- 已完成的实现计划
│   ├── tech-debt-tracker.md      <- 技术债
│   └── product-debt-tracker.md   <- 产品语义债
├── references/
│   ├── index.md                  <- 本地外部参考索引
│   └── ...
└── superpowers/                  <- 既有历史产物；新事实按上方 docs 轨道归档
```
