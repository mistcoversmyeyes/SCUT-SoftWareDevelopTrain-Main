# SCUT WMS — 仓储管理系统

华南理工大学软件工程实训项目。

## 技术栈

- 后端: Spring Boot 3 + MyBatis-Plus + MySQL 8.0
- 前端: Vue 3 + Vite + Element Plus

## 快速启动

### 1. 启动 MySQL

```bash
docker-compose up -d mysql
```

或手动安装 MySQL 8.0，确保 `root/root` 可连接 `127.0.0.1:3306`。

### 2. 初始化数据库（仅首次）

```bash
# 建表
mysql -u root -proot -h 127.0.0.1 < backend/src/main/resources/schema.sql

# 导入种子数据（含供应商、物料、容器类型、库位、真实库存）
mysql -u root -proot -h 127.0.0.1 scut_wms < scripts/seed-data.sql
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -DskipTests
```

后端运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`。

### 5. 登录

用户名 `admin`，密码 `123456`。

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
