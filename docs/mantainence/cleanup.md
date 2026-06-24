# Cleanup

本文件记录 WMS 本地服务停止、容器停止和数据卷清理命令。

## Stop Backend And Frontend

停止脚本启动的前后端进程：

```bash
pkill -f "spring-boot:run"
pkill -f "vite --host 0.0.0.0"
```

## Stop MySQL

停止 MySQL 容器但保留数据卷：

```bash
docker compose down || docker-compose down
```

也可直接操作容器：

```bash
docker stop scut-wms-mysql
docker rm scut-wms-mysql
```

## Remove MySQL Data Volume

连 MySQL 数据卷一起清理：

```bash
docker compose down -v || docker-compose down -v
```

该命令会删除本地数据库数据。需要重新开始演示数据时，清理后按 `database.md` 重新初始化。
