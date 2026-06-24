# Database

本文件记录 WMS 本地数据库连接、初始化和重置流程。运行环境前提见 `environment.md`。

## Local Config

- 数据库服务：`docker-compose.yml` 中的 `mysql`，容器名 `scut-wms-mysql`。
- 数据库名：`scut_wms`；测试库：`scut_wms_test`。
- 默认数据库账号：`root` / `root`。
- 后端默认连接：

```text
jdbc:mysql://127.0.0.1:3306/scut_wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
```

可通过环境变量覆盖：

```bash
export WMS_DB_URL='jdbc:mysql://127.0.0.1:3306/scut_wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export WMS_DB_USERNAME='root'
export WMS_DB_PASSWORD='root'
```

后端启动时由 `DatabaseMigration` 兼容创建或补齐表结构；`spring.sql.init.mode` 当前为 `never`，不会自动执行 destructive SQL 初始化。

## Clean Initialization Or Reset

当 MySQL 数据卷是全新的，或希望用固定演示数据重新开始验收时，建议显式初始化 schema 和 seed 数据。

```bash
cd /home/yuming/scut/SCUT_26_spring/software_develop_train
docker compose up -d mysql || docker-compose up -d mysql
```

等待 MySQL 就绪：

```bash
mysql -h 127.0.0.1 -uroot -proot -e "SELECT 1"
```

重建本地演示库并导入数据：

```bash
mysql -h 127.0.0.1 -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 127.0.0.1 -uroot -proot scut_wms < backend/src/main/resources/schema.sql
mysql -h 127.0.0.1 -uroot -proot scut_wms < scripts/seed-data.sql
```

如需测试库：

```bash
mysql -h 127.0.0.1 -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

`backend/src/main/resources/schema.sql` 会 `DROP TABLE IF EXISTS`，只用于本地重置，不要对需要保留数据的库执行。
