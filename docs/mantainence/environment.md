# Environment

本文件记录本地运行 WMS 的环境前提、端口约定和 WSL 注意事项。

## Runtime

- Java 17 及 Maven，可运行 Spring Boot 3.3.5 后端。
- Node.js 与 npm，可运行 Vue 3 / Vite 前端。
- Docker 与 `docker compose` 或 `docker-compose`，用于启动 MySQL 8。
- 本机可用 MySQL 客户端 `mysql`；`scripts/start.sh` 会用它检测数据库并导入种子数据。

## WSL Node Path

WSL Agent Sandbox 环境建议先加载 nvm，避免误用 Windows 侧 `npm`：

```bash
source ~/.nvm/nvm.sh
which node
which npm
```

`which npm` 应优先指向 WSL 内路径，例如 `/home/yuming/.nvm/.../bin/npm`。如果指向 `/mnt/c/Program Files/nodejs/npm`，前端测试和启动可能在 UNC 路径下失败。

## Ports

- MySQL：`127.0.0.1:3306`
- Backend：`http://localhost:8080`
- Frontend：`http://localhost:5173`

启动前确认这些端口未被旧进程占用。端口冲突处理见 `cleanup.md` 和 `troubleshooting.md`。
