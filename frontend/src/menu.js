import { Box, DataAnalysis, HomeFilled, Tickets, Van } from '@element-plus/icons-vue'

export const menuItems = [
  {
    key: 'dashboard',
    title: '首页',
    path: '/dashboard',
    icon: HomeFilled,
    description: '查看仓储运行概览、待办事项和基础统计。',
    fields: ['今日入库：12 单', '今日出库：8 单', '库存预警：3 项']
  },
  {
    key: 'materials',
    title: '物料信息',
    path: '/materials',
    icon: Box,
    description: '维护汽车零部件物料编码、名称、规格和单位。',
    fields: ['物料编码', '物料名称', '规格型号', '计量单位']
  },
  {
    key: 'inbound',
    title: '入库管理',
    path: '/inbound',
    icon: Tickets,
    description: '跟踪采购到货、质检完成和上架入库流程。',
    fields: ['入库单号', '供应商', '到货数量', '质检状态']
  },
  {
    key: 'inventory',
    title: '库存监控',
    path: '/inventory',
    icon: DataAnalysis,
    description: '查看当前库存数量、安全库存和库位状态。',
    fields: ['当前库存', '安全库存', '库位编号', '预警状态']
  },
  {
    key: 'outbound',
    title: '出库管理',
    path: '/outbound',
    icon: Van,
    description: '处理销售出库、领料出库和发运状态。',
    fields: ['出库单号', '客户名称', '拣货状态', '发运状态']
  }
]

export function findMenuItem(key) {
  return menuItems.find((item) => item.key === key)
}
