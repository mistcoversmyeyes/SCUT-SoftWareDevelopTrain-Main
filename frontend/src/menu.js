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
    key: 'inbound-orders',
    title: '入库订单',
    path: '/inbound/orders',
    icon: Tickets,
    description: '查看和管理采购入库单。',
    fields: ['入库单号', '供应商', '状态', '到货时间']
  },
  {
    key: 'inbound-scan',
    title: '入库扫码',
    path: '/inbound/scan',
    icon: Tickets,
    description: '扫码完成在途看板收货并更新库存。',
    fields: ['看板码', '扫码结果', '更新数量', '最近操作时间']
  },
  {
    key: 'inventory-balances',
    title: '库存余额',
    path: '/inventory/balances',
    icon: DataAnalysis,
    description: '按物料、仓库和库位查看当前库存。',
    fields: ['物料', '仓库', '库位', '当前库存']
  },
  {
    key: 'inventory-trace',
    title: '库存追溯',
    path: '/inventory/trace',
    icon: DataAnalysis,
    description: '查看入库流水与库存变更历史。',
    fields: ['流水号', '看板码', '入库单号', '时间']
  },
  {
    key: 'kanbans-trace',
    title: '看板追溯',
    path: '/kanbans/trace',
    icon: Van,
    description: '输入看板码查询其生成与收货全过程。',
    fields: ['看板码', '入库单号', '状态', '最近更新时间']
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
