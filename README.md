# SCUT WMS — 仓储管理系统

华南理工大学软件工程实训项目。

## 技术栈

- 后端: Spring Boot 3 + MyBatis-Plus + MySQL 8.0
- 前端: Vue 3 + Vite + Element Plus

## 前置条件

- Java 17+, Maven 3.9+, Node.js 20+
- Docker 或 MySQL 8.0
- Ubuntu 20.04+ 或 Windows (Git Bash)

## 快速启动（一键）

```bash
# 1. 启动 MySQL（仅首次）
docker-compose up -d mysql

# 2. 一键启动
bash scripts/start.sh
```

启动完成后访问 `http://localhost:5173`，用户名 `admin`，密码 `123456`。

`start.sh` 会自动完成：MySQL 就绪检测 → 首次导入种子数据 → 启动后端 → 启动前端。数据库表结构由 `DatabaseMigration` 启动时自动创建，无需手动执行 `schema.sql`。

## 手动启动

```bash
# 1. MySQL
docker-compose up -d mysql

# 2. 种子数据（仅首次）
mysql -u root -proot -h 127.0.0.1 scut_wms < scripts/seed-data.sql

# 3. 后端
cd backend && mvn spring-boot:run -DskipTests

# 4. 前端
cd frontend && npm install && npm run dev
```

## 架构

```
MySQL(:3306) → Spring Boot(:8080) → Vue 前端(:5173)
```

## 模块

- **基础数据**: 供应商、物料、容器类型、仓库库位
- **入库管理**: 入库单创建 → 释放(生成看板) → 打印看板 → 扫码入库
- **出库管理**: 出库单创建 → 释放(锁库) → 带单/不带单/强制 扫码出库
- **库存管理**: 库存总览(库位容量 + 物料充足性)、库存追溯、流水
- **锁库管理**: 看板锁记录、解锁、强制出库审计
