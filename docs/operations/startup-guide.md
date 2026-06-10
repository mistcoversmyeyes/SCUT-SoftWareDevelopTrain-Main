# WMS 入库模块启动与清理手册

本文档汇总本仓库本地运行/调试所需命令，默认使用 `dev/iter2` 当前工作树。

## 一、启动依赖

1. 安装并启动 Docker，当前仓库依赖 MySQL 8 服务。
2. 确保 `scripts/start-mysql.sh` 与 `scripts/start.sh` 可访问（在无执行权限环境下用 `bash` 调用）。
3. 确认本地端口未被占用：
   - MySQL：`3306`
   - Backend：`8080`
   - Frontend：`5173`

## 二、一键启动（推荐）

执行顺序固定为：MySQL → 后端 → 前端。脚本位于仓库根目录。

```bash
cd /home/yuming/scut/SCUT_26_spring/SCUT-SoftWareDevelopTrain-Main
bash scripts/start.sh
```

`start.sh` 会按顺序启动：

- `scripts/start-mysql.sh`（Docker MySQL，创建 `scut_wms`、`scut_wms_test`）
- 后端：`mvn spring-boot:run -Dspring-boot.run.profiles=local`（后台）
- 前端：`npm run dev`（后台）

日志路径：

- 后端：`/tmp/backend.log`
- 前端：`/tmp/frontend.log`

启动后访问：

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`

## 三、手动启动（分步）

### 1) 启动 MySQL 容器

```bash
cd /home/yuming/scut/SCUT_26_spring/SCUT-SoftWareDevelopTrain-Main
sudo docker compose up -d mysql
```

### 2) 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3) 启动前端

```bash
cd frontend
npm install --silent
npm run dev -- --host 0.0.0.0 --port 5173
```

## 四、服务清理

### A. 关闭前后端进程

```bash
pkill -f "mvn spring-boot:run -Dspring-boot.run.profiles=local"
pkill -f "vite --host 0.0.0.0 --port 5173"
```

### B. 停止数据库服务

```bash
sudo docker stop scut-wms-mysql
sudo docker rm scut-wms-mysql
```

或在仓库根目录执行：

```bash
sudo docker compose down
```

如需连数据库数据一起清理：

```bash
sudo docker compose down -v
```

## 五、快速故障排查

- `Cannot connect to the Docker daemon`：当前用户无 docker socket 权限，改用 `sudo` 或补充用户组后重登。
- 后端启动提示 `Communications link failure`：确认 MySQL 容器健康且端口 `3306` 已监听。
- 前端启动提示 `listen EPERM`：常见于权限/策略限制，建议在本机终端直接启动，而不是受限沙箱会话。
