# SCUT WMS - 仓储管理系统

华南理工大学软件工程实训项目。

## 技术栈

- Backend: Java 17, Spring Boot 3.3.5, Maven, MyBatis-Plus, MySQL 8.0
- Frontend: Vue 3, Vite, Element Plus, Pinia, Vue Router, Axios, Vitest
- Local runtime: Docker / Docker Compose, MySQL client, Node.js/npm
- 项目运维文档族：`docs/mantainence/index.md`

## 快速启动

切换到 `main` 分支，同步云端仓库：

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train
git checkout main
git pull origin main:main
``` 

首次使用全新 MySQL 数据卷，或需要重置演示数据时，先按 `docs/mantainence/index.md` 的数据库入口执行。已有本地演示库时可直接启动：

```bash
bash scripts/start.sh
```

启动完成后访问：

- Web 前端：`http://localhost:5173`
- 后端服务：`http://localhost:8080`
- 手机端 H5：`http://localhost:5173/mobile/inbound`
- 登录账号：`admin` / `123456`


## 用户界面介绍

主要 Web 页面：

- 入库：`/inbound/orders`、`/inbound/scan`、`/inbound/history`
- 出库：`/outbound/orders`、`/outbound/pick-with-order`、`/outbound/pick-no-order`、`/outbound/locks`、`/outbound/history`
- 库存：`/inventory/overview`、`/inventory/balances`、`/inventory/trace`、`/inventory/ai-import`
- 看板：`/kanbans/list`、`/kanbans/trace`
- 手机端：`/mobile/inbound`、`/mobile/outbound`、`/mobile/kanban`

AI 导入样例：`frontend/public/samples/week4-inventory-flow-history-sample.csv`，页面下载入口为 `http://localhost:5173/samples/week4-inventory-flow-history-sample.csv`。

## 日志与清理

一键启动日志：

- 后端：`/tmp/wms-backend.log`
- 前端：`/tmp/wms-frontend.log`

停止、清理数据库、端口占用和常见故障处理见 `docs/mantainence/index.md`。
