# Tech Debt Tracker

技术债记录可通过实现、测试、重构、CI 或工具化解决的问题。

| ID | Area | Debt | Impact | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| TD-001 | CI | 尚未发现仓库级 CI，测试与构建仍依赖本地执行 | 后续合并缺少自动验证门禁 | TBD | open |
| TD-002 | Auth | 当前认证是内存账号和演示 token | 不能支撑正式用户、角色、权限 | TBD | open |
| TD-003 | Data | 当前没有数据库与业务持久化层 | 入库、出库、库存等核心流程无法真实落地 | TBD | open |

