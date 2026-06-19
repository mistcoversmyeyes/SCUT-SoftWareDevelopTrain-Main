import { Box, DataAnalysis, HomeFilled, Tickets, Van } from '@element-plus/icons-vue'

export const menuItems = [
  { key: 'dashboard', title: '首页', path: '/dashboard', icon: HomeFilled,
    description: '查看仓储运行概览、待办事项和基础统计。',
    fields: ['今日入库', '今日出库', '库存预警'] },
  { key: 'master-data', title: '基础数据', icon: Box, children: [
      { key: 'suppliers', title: '供应商管理', path: '/master-data/suppliers',
        description: '维护供应商编码、名称和联系方式。', fields: ['供应商编码', '供应商名称', '联系人'] },
      { key: 'materials', title: '物料信息', path: '/master-data/materials',
        description: '维护汽车零部件物料编码、名称、规格和单位。', fields: ['物料编码', '物料名称', '规格型号'] },
      { key: 'containers', title: '器具管理', path: '/master-data/containers',
        description: '维护周转箱、托盘等包装器具信息。', fields: ['器具编码', '器具名称', '包装容量'] },
      { key: 'warehouses', title: '仓库库位', path: '/master-data/warehouses',
        description: '维护仓库和存储库位信息。', fields: ['仓库编码', '库位编码'] }
    ]},
  { key: 'inbound', title: '入库管理', icon: Tickets, children: [
      { key: 'inbound-orders', title: '入库单', path: '/inbound/orders',
        description: '查看和管理采购入库单。', fields: ['入库单号', '供应商', '状态'] },
      { key: 'inbound-scan', title: '入库扫码', path: '/inbound/scan',
        description: '扫码完成在途看板收货并更新库存。', fields: ['看板码', '扫码结果'] },
      { key: 'inbound-history', title: '入库历史', path: '/inbound/history',
        description: '查看已完成和已取消的入库单记录。', fields: ['入库单号', '完成时间'] }
    ]},
  { key: 'outbound', title: '出库管理', icon: Van, children: [
      { key: 'outbound-orders', title: '出库单', path: '/outbound/orders',
        description: '处理生产领料、退货等出库业务。', fields: ['出库单号', '出库用途'] },
      { key: 'outbound-pick-with-order', title: '带单出库', path: '/outbound/pick-with-order',
        description: '扫描出库单二维码，按锁定指引执行 FIFO 拣货。', fields: ['出库单号', '锁定物料'] },
      { key: 'outbound-pick-no-order', title: '不带单出库', path: '/outbound/pick-no-order',
        description: '直接扫描看板码出库（强制出库）。', fields: ['看板码'] },
      { key: 'outbound-locks', title: '锁货管理', path: '/outbound/locks',
        description: '查看、解锁、重新分配出库锁定记录。', fields: ['出库单号', '锁状态'] },
      { key: 'outbound-history', title: '出库历史', path: '/outbound/history',
        description: '查看已完成和已取消的出库单记录。', fields: ['出库单号', '完成时间'] }
    ]},
  { key: 'inventory', title: '库存监控', icon: DataAnalysis, children: [
      { key: 'inventory-overview', title: '库存总览', path: '/inventory/overview',
        description: '库位使用率与物料充足性总览仪表盘。', fields: ['库位容量', '物料库存'] },
      { key: 'inventory-balances', title: '当前库存', path: '/inventory/balances',
        description: '按物料、仓库和库位查看当前库存。', fields: ['物料', '仓库', '库位'] },
      { key: 'inventory-trace', title: '库存追溯', path: '/inventory/trace',
        description: '查看入库流水与库存变更历史。', fields: ['流水号', '入库单号'] }
    ]},
  { key: 'kanbans', title: '看板信息', icon: Van, children: [
      { key: 'kanbans-list', title: '看板列表', path: '/kanbans/list',
        description: '按状态、入库单、物料等维度查看看板。', fields: ['看板码', '状态'] },
      { key: 'kanbans-trace', title: '看板追溯', path: '/kanbans/trace',
        description: '输入看板码查询其生成与收货全过程。', fields: ['看板码', '入库单号'] }
    ]}
]

export function findMenuItem(key, items = menuItems) {
  for (const item of items) {
    if (item.key === key) return item
    if (item.children) {
      const found = findMenuItem(key, item.children)
      if (found) return found
    }
  }
  return null
}

export function flattenMenuItems(items = menuItems) {
  return items.flatMap(item => {
    if (item.children) return flattenMenuItems(item.children)
    return [item]
  })
}
