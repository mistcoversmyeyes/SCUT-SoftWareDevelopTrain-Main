# Mantainence Index

本目录存放项目运维文档，覆盖本地启动、运行检查、停止清理、故障排查和后续部署维护资料。
新增、移动、删除运维文档时，必须同步本索引。

## Current Docs

- `environment.md`：本地运行前提、端口约定和 WSL/npm 注意事项。
- `startup.md`：一键启动、手动分步启动、启动后访问入口和日志路径。
- `database.md`：数据库连接配置、schema/seed 初始化、本地重置和测试库创建。
- `verification.md`：后端登录接口、前端首页、数据库表和日志检查。
- `cleanup.md`：停止前后端、停止 MySQL、清理数据卷。
- `troubleshooting.md`：Docker、MySQL、前端环境和端口占用常见故障。

## Task Routing

- 准备环境：读 `environment.md`。
- 启动系统：读 `startup.md`，必要时先读 `database.md`。
- 重置演示库：读 `database.md`。
- 确认系统可用：读 `verification.md`。
- 停止或清理：读 `cleanup.md`。
- 排查启动失败：读 `troubleshooting.md`，再回到对应主题文档。
