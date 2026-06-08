# Architecture Map

## Product Context

本项目是面向汽车企业供应链/仓储场景的 WMS 仓储管理系统训练项目，目标是围绕仓储效率和精细化管理逐步实现 Web 前端、服务器端以及后续可能的安卓手持扫码端协同。

产品背景资料显示的核心业务范围包括：

- 入库单制作、入库状态跟踪、唯一看板打印、手持扫码入库。
- 条码过程状态监控。
- 出库单、带单/不带单扫码出库。
- 转包、封存、解封、退库。
- 库存/看板监控、库位库存、高低储预警。
- 零件扫码防错、先进先出。
- 供应商、客户、零件、器具、仓库、库位等基础信息。
- 角色、权限、用户管理。

当前代码只实现了第 1 周登录与菜单标签联动雏形，不代表完整 WMS 闭环已经具备。

## Source Layout

```text
backend/
  pom.xml
  src/main/java/com/scut/wms/
    WmsApplication.java
    auth/       # 登录、当前用户、演示 token
    config/     # CORS 与全局异常处理
  src/test/java/com/scut/wms/

frontend/
  package.json
  vite.config.js
  src/
    api/        # axios 与认证 API
    components/ # 菜单、标签栏等布局组件
    router/     # Vue Router 与登录守卫
    stores/     # Pinia auth/tabs 状态
    views/      # 登录页、主布局、WMS 占位页
    menu.js     # 菜单与 WMS 模块元数据

res/
  WMS仓储管理系统--产品介绍资料.pdf
```

## Runtime Shape

```text
Browser
  -> Vite dev server :5173
    -> /api proxy
      -> Spring Boot :8080
```

当前前端通过 `vite.config.js` 将 `/api` 代理到 `http://localhost:8080`。后端暴露 `/api/auth/login` 与 `/api/auth/me`；认证仍是演示账号和演示 token，不是正式权限体系。

## Dependency Direction

- 前端页面依赖 `frontend/src/menu.js` 的菜单元数据，不应在多个组件重复硬编码 WMS 模块列表。
- 前端 API 层经 `frontend/src/api/http.js` 统一设置 baseURL 和 token header。
- 后端 Controller 只处理 HTTP 边界，认证规则放在 `AuthService`。
- 未来业务模块应按领域划分包，例如 inbound、outbound、inventory、barcode、masterdata、users；不要把所有业务继续塞进 auth 或 config。

## External Boundaries

- PDF 中提到 MES、ERP、SAP 等接口只是外部系统边界，不代表当前仓库已经接入。
- 安卓手持 app 是产品背景中的终端形态；当前仓库尚未出现安卓子项目。
- 数据库、真实权限、条码设备、接口协议均需要进入 spec 后再实现。

