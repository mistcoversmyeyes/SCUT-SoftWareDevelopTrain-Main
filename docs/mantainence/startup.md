# Startup

本文件记录 WMS 本地一键启动和手动分步启动命令。运行前提见 `environment.md`，数据库初始化或重置见 `database.md`。

## 一键启动

适用场景：本地 MySQL 已有表结构，或已经按 `database.md` 完成干净初始化或重置。

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train
source ~/.nvm/nvm.sh
bash scripts/start.sh
```

`scripts/start.sh` 会按顺序执行：

1. 通过 `docker compose up -d mysql` 或 `docker-compose up -d mysql` 启动 MySQL。
2. 通过本机 `mysql` 客户端等待 `127.0.0.1:3306` 就绪。
3. 如果 `scut_wms.supplier` 不存在，则导入 `scripts/seed-data.sql`。
4. 后台启动后端：`mvn spring-boot:run -DskipTests -q`。
5. 后台启动前端：`npm run dev -- --host 0.0.0.0`。

日志路径：

- 后端：`/tmp/wms-backend.log`
- 前端：`/tmp/wms-frontend.log`

启动成功后访问：

- Web 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- 手机端 H5：`http://localhost:5173/mobile/inbound`、`/mobile/outbound`、`/mobile/inventory-tag`

启动后可用性检查见 `verification.md`。

## 手动启动

### 1. Start MySQL

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train
docker compose up -d mysql || docker-compose up -d mysql
```

也可以只启动数据库并创建 `scut_wms`、`scut_wms_test`：

```bash
bash scripts/start-mysql.sh
```

该脚本当前直接调用 `docker-compose`。如果本机只有 Docker Compose v2 插件，可使用上一条 `docker compose up -d mysql` 命令。

### 2. Start Backend

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train/backend
mvn spring-boot:run -DskipTests
```

如果需要指定本地 profile：

```bash
mvn spring-boot:run -DskipTests -Dspring-boot.run.profiles=local
```

### 3. Start Frontend

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train/frontend
source ~/.nvm/nvm.sh
npm install
npm run dev -- --host 0.0.0.0
```

Vite 默认端口为 `5173`，代理 `/api` 到后端 `8080`。
