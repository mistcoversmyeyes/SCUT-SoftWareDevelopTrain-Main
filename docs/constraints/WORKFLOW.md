# Workflow

## Lifecycle Decision

本项目采用计划驱动的阶段式迭代增量模型。

判断依据：

- 项目背景资料较完整，产品领域和主要模块已经明确，不适合按敏捷模型从模糊需求中持续发现产品形态。
- 当前课程/项目按周推进，但每周产物不是围绕核心业务闭环递增的 MVP。
- 早期周迭代可能只完成登录、菜单、标签页、占位模块、基础联调等工程地基；这些不是可对外发布的 WMS 核心版本。

因此，本项目使用“需求/背景基线 -> 周迭代计划 -> 工作包规格 -> 执行计划 -> 验收记录 -> 债务/下一周输入”的流程。

## Standard Flow

```text
产品背景与需求基线
  -> docs/iterations/weekN-*.md
  -> docs/specs/<wp-or-module>.md
  -> docs/exec-plans/active/<plan>.md
  -> implementation + tests
  -> docs/exec-plans/completed/<plan>.md
  -> tech debt / product debt
  -> next iteration input
```

## Iteration Rules

- 周迭代文件只描述阶段目标、工作包、验收方式、边界和已知风险。
- 周迭代不得承诺“本周就是 MVP”，除非规格明确证明已形成可验收业务闭环。
- 每个工作包进入实现前，应有足够规格说明：行为、接口、字段口径、验收标准。
- 未决业务语义进入 product debt；可通过编码、测试、重构解决的问题进入 tech debt。
- 既有 `docs/superpowers/` 文档可作为历史上下文，但新事实应归档到本 workflow 指定位置。

## Verification Gates

- 后端变更：`mvn test`。
- 前端逻辑变更：`npm test`。
- 前端路由、构建配置、依赖、样式主路径变更：`npm run build`。
- 联调或 UI 交付：启动前后端并记录浏览器验证或截图路径。

## Change Handling

- 若产品背景 PDF 与代码现状冲突，优先把冲突写入 spec 或 product debt，再决定实现。
- 若每周作业要求与长期 WMS 架构冲突，周作业可以先局部满足，但必须记录为技术债或产品债。
- 若后续出现数据库、手持终端、条码设备、MES/ERP/SAP 接口，先补 spec，不直接写实现。

## Harden Candidates

- TODO(harden): CI 中固定后端、前端测试和构建命令。
- TODO(harden): 检查新执行计划是否只出现在 `docs/exec-plans/active/` 或 `completed/`。
- TODO(harden): 检查周迭代文档只进入 `docs/iterations/`。

