# Verification

本文件记录 WMS 本地启动后的基础可用性检查。启动命令见 `startup.md`。

## Backend Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

## Frontend Home

```bash
curl -I http://localhost:5173
```

## Database Tables

```bash
mysql -h 127.0.0.1 -uroot -proot scut_wms -e "SHOW TABLES;"
```

## Logs

```bash
tail -f /tmp/wms-backend.log
tail -f /tmp/wms-frontend.log
```

演示账号：

- username：`admin`
- password：`123456`
