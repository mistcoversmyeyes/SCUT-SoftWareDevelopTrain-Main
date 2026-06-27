# Specs Index

- 本目录存放需求、设计、工作包和模块规格；规格先描述行为、接口、字段口径和验收，再指导实现。
- 新增、移动、删除规格文件时，必须同步本索引。
- 规格文件命名统一为 `YYYY-MM-DD-<business-topic>-<doc-kind>.md`；日期取规格内容最初创建日期，文件名不使用 `weekN` 或 `module-*` 作为信息壳。

## Current Specs

- `2026-06-03-wms-login-tabs-design.md`：Week 1 登录、后台主界面、菜单与标签页联动设计，覆盖前后端登录打通和课堂演示边界。
- `2026-06-10-inbound-core-design.md`：Week 2 采购入库核心功能设计，覆盖入库单、库存标签、扫码入库、库存追溯、库存标签追溯和 MySQL 持久化边界。
- `2026-06-10-inbound-data-model-review.md`：采购入库数据模型审查，记录原始 11 表方案、范式审查和最终 9 表持久化模型。
- `2026-06-15-outbound-master-data-inbound-enhancement-design.md`：Week 3 出库管理、基础数据 CRUD、入库增强、库存预警和库存标签生命周期设计。
- `2026-06-17-lock-goods-design.md`：Week 4 锁货功能设计，覆盖出库释放锁货、带单/不带单出库、强制出库、锁货管理和审计。
- `2026-06-23-wms-completion-requirements.md`：Week 4 总体需求规格，覆盖业务补全、散件出库、封存解封、手机端、监控历史、批量导入和 AI 预警准备。
- `2026-06-23-ai-warning-scope.md`：Week 4 WP4-05 AI 预警方向规格，覆盖方向取舍、字段清单、规则型预警雏形、导入联动和后续产品债出口。
- `2026-06-23-ai-data-import-template.md`：Week 4 WP4-04 首期表格导入模板规格（历史文件名沿用 ai-data-import-template），冻结 inventory_flow_history CSV 模板、校验规则、独立落表和样例文件位置。
- `2026-06-24-acceptance-closure-fix-design.md`：Iteration 4 验收闭环修复设计，覆盖 FR-02 出库阻断、FR-03 封存出库联动、库存标签命名迁移和入库单待收货状态迁移。
- `2026-06-24-mobile-real-scan-design.md`：手机端真实扫码设计，覆盖库存标签码和出库单二维码的摄像头扫描、图片识别、手动兜底、异常处理和验收方式。
- `2026-06-27-iter5-batch-inbound-outbound-requirements.md`：Iteration 5 需求规格，覆盖批量入库创建、批量出库 FIFO 推荐、手机端待处理出库单、库存标签批量打印、库存详情短缺筛选和手机端封存/解封。
