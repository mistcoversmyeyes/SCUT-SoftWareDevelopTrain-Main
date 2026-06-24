# Tech Debt Tracker

技术债记录可通过实现、测试、重构、CI 或工具化解决的问题。

| ID | Area | Debt | Impact | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| TD-001 | CI | 尚未发现仓库级 CI，测试与构建仍依赖本地执行 | 后续合并缺少自动验证门禁 | TBD | open |
| TD-002 | Auth | 当前认证是内存账号和演示 token | 不能支撑正式用户、角色、权限 | TBD | open |
| TD-003 | Data | 演示数据库迁移仍由启动时兼容脚本维护，缺少正式版本化迁移与种子数据策略 | 字段演进和演示数据准备依赖人工校验 | TBD | open |
| TD-004 | Migration | `DatabaseMigration` 在 H2 测试环境仍存在元数据兼容告警 | 测试输出有噪声，后续迁移失败可能被误判为可忽略告警 | TBD | open |
| TD-005 | Frontend Build | 前端主包构建存在 chunk size warning | 随功能继续扩展会影响首屏加载和构建可读性 | TBD | open |
| TD-006 | History API | 首期出入库历史部分筛选由前端二次过滤承接 | 数据量增大后性能和分页一致性不足 | TBD | open |

