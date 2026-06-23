# Troubleshooting

本文件记录 WMS 本地运行常见故障和处理入口。

## Docker

- `Cannot connect to the Docker daemon`：Docker 未启动，或当前用户无 docker socket 权限。先启动 Docker；Linux/WSL 下可改用 `sudo` 或加入 docker 用户组后重新登录。

## MySQL

- `mysql: command not found`：安装 MySQL client，或使用能提供 `mysql` 命令的环境；`scripts/start.sh` 依赖本机 `mysql` 客户端。
- 干净库执行 `scripts/start.sh` 时导入 seed 失败：先按 `database.md` 执行 schema 初始化，再运行一键启动。
- 后端提示 `Communications link failure`：确认 MySQL 容器健康、`3306` 已监听，且 `WMS_DB_URL` 指向正确数据库。

## Frontend

- 前端测试或启动出现 UNC 路径、`vitest` 找不到、`npm` 指向 `/mnt/c/Program Files/nodejs/npm`：在 WSL 中先执行 `source ~/.nvm/nvm.sh`，确认 `which npm` 指向 WSL 内路径。

## Ports

- `5173` 或 `8080` 端口占用：停止旧的 Vite/Spring Boot 进程，或临时改用其他端口启动前端。
