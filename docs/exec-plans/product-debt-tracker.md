# Product Debt Tracker

产品债记录需要业务、课程要求或用户决策才能确认的问题。

| ID | Area | Question | Needed Decision | Status |
| --- | --- | --- | --- | --- |
| PD-001 | Scope | Web、服务器、安卓手持 app 的课程交付边界是什么 | 是否只实现 Web+后端，还是需要手持端原型或接口模拟 | open |
| PD-002 | Inbound | 入库单、唯一看板、二维码字段口径尚未确认 | 确认入库单字段、看板编码规则、扫码校验规则 | open |
| PD-003 | Inventory | 高低储预警和 FIFO 是否需要强制业务规则 | 确认库存预警阈值、FIFO 违规处理方式 | open |
| PD-004 | Integration | MES/ERP/SAP 接口只是背景能力还是课程要求 | 确认是否需要接口设计、mock 或真实对接 | open |
| PD-005 | Packaging | 器具、容器、包装容量和装箱规则是否进入入库闭环 | 确认 `container_type`、容器实例、装箱/拆箱/合箱规则和看板数量口径 | open |
| PD-006 | Scan Audit | 失败扫码、设备日志、PDA 操作日志是否需要持久化 | 确认是否新增 `scan_attempt_log`，以及失败原因、设备、操作人、原始码字段 | open |
| PD-007 | Printing | 看板和入库单是否需要 PDF 导出、真实打印机或标签机协议 | 确认打印输出格式、纸张/标签尺寸、二维码生成与设备对接要求 | open |
| PD-008 | Master Data | 供应商、物料、仓库、库位是否需要完整维护流程 | 确认基础资料 CRUD、启停用、导入导出、校验规则和权限边界 | open |
