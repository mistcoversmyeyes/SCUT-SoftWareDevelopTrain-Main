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

## AI API

AI 预警的风险等级由本地规则计算；大模型 API 仅用于生成仓储建议报告。

可选环境变量：

```bash
export WMS_AI_API_KEY='your-api-key'
export WMS_AI_BASE_URL='https://api.deepseek.com'
export WMS_AI_MODEL='deepseek-v4-flash'
export WMS_AI_API_FORMAT='auto'
```

推荐把本地私有配置放在项目根目录 `.env.local`，`scripts/start.sh` 会自动加载该文件，且 `.gitignore` 已忽略 `.env` 和 `.env.*`：

```bash
WMS_AI_API_KEY=your-api-key
WMS_AI_BASE_URL=https://api.deepseek.com
WMS_AI_MODEL=deepseek-v4-flash
WMS_AI_API_FORMAT=auto
```

`WMS_AI_API_FORMAT` 可选值为 `auto`、`responses`、`chat-completions`。默认 `auto` 会在 `WMS_AI_BASE_URL=https://api.deepseek.com` 时使用 `/chat/completions`，其他场景默认使用 OpenAI Responses API 的 `/responses`。

如果不配置 `WMS_AI_API_KEY`，`GET /api/ai-warning/analysis/inventory-risk-report` 会返回 `NOT_CONFIGURED`，不影响 CSV 导入和规则型预警分析。

不要把真实 API Key 写入仓库、文档或提交记录。
