const DEFAULT_LEAD_TIME_DAYS = 7
const DEFAULT_SAFETY_STOCK_DAYS = 3
const DEFAULT_STAGNANT_DAYS = 45
const WARNING_RISK_CODES = new Set(['WATCH', 'HIGH', 'CRITICAL', 'EXPIRED'])

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function parseDate(value) {
  if (!value) {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function daysBetween(later, earlier) {
  if (!later || !earlier) {
    return null
  }
  const diff = startOfDay(later).getTime() - startOfDay(earlier).getTime()
  return Math.max(Math.floor(diff / 86400000), 0)
}

function isWithinDays(date, today, days) {
  const distance = daysBetween(today, date)
  return distance !== null && distance < days
}

function includesText(source, keyword) {
  if (!keyword) {
    return true
  }
  return String(source || '').toLowerCase().includes(String(keyword).trim().toLowerCase())
}

function inDateRange(value, range) {
  if (!range || range.length !== 2) {
    return true
  }
  const target = parseDate(value)
  const start = parseDate(range[0])
  const end = parseDate(range[1])
  if (!target || !start || !end) {
    return false
  }
  return startOfDay(target) >= startOfDay(start) && startOfDay(target) <= startOfDay(end)
}

function summarizeMaterialCodes(codes) {
  const unique = [...new Set(codes.filter(Boolean))]
  if (!unique.length) {
    return '—'
  }
  if (unique.length <= 2) {
    return unique.join(' / ')
  }
  return `${unique.slice(0, 2).join(' / ')} 等 ${unique.length} 种物料`
}

function buildFlowInsightMap(flowRecords = [], today = new Date()) {
  const now = parseDate(today) || new Date()
  const insights = new Map()

  for (const record of flowRecords) {
    const materialCode = record?.materialCode
    if (!materialCode) {
      continue
    }
    const current = insights.get(materialCode) || {
      materialCode,
      totalRecords: 0,
      totalOutbound7d: 0,
      totalOutbound30d: 0,
      lastOutboundDate: null,
      lastInboundDate: null,
      lastMovementDate: null
    }
    const businessDate = parseDate(record.businessDate)
    const movementType = String(record.movementType || '').toUpperCase()
    const quantity = toNumber(record.quantity)

    current.totalRecords += 1
    if (businessDate && (!current.lastMovementDate || businessDate > current.lastMovementDate)) {
      current.lastMovementDate = businessDate
    }

    if (movementType === 'OUTBOUND') {
      if (businessDate && (!current.lastOutboundDate || businessDate > current.lastOutboundDate)) {
        current.lastOutboundDate = businessDate
      }
      if (businessDate && isWithinDays(businessDate, now, 7)) {
        current.totalOutbound7d += quantity
      }
      if (businessDate && isWithinDays(businessDate, now, 30)) {
        current.totalOutbound30d += quantity
      }
    }

    if (movementType === 'INBOUND' && businessDate && (!current.lastInboundDate || businessDate > current.lastInboundDate)) {
      current.lastInboundDate = businessDate
    }

    insights.set(materialCode, current)
  }

  return insights
}

function buildStockState(row, material) {
  const availableQty = toNumber(row.availableQty)
  const onHandQty = toNumber(row.onHandQty)
  const lowStockQty = toNumber(material?.lowStockQty)
  const highStockQty = toNumber(material?.highStockQty)

  if (availableQty <= 0) {
    return { code: 'OUT_OF_STOCK', label: '缺货', tone: 'danger' }
  }
  if (lowStockQty > 0 && availableQty <= lowStockQty) {
    return { code: 'LOW', label: '低储', tone: 'warning' }
  }
  if (highStockQty > 0 && onHandQty >= highStockQty) {
    return { code: 'HIGH', label: '高储', tone: 'info' }
  }
  return { code: 'NORMAL', label: '正常', tone: 'success' }
}

function buildShortageRisk(row, material, insight) {
  const availableQty = toNumber(row.availableQty)
  const lowStockQty = toNumber(material?.lowStockQty)

  if (availableQty <= 0) {
    return {
      code: 'CRITICAL',
      label: '紧急',
      tone: 'danger',
      reason: '当前可用库存为 0，需要立即补货或释放占用。'
    }
  }

  if (!insight || !insight.totalRecords) {
    return {
      code: 'DATA_UNPREPARED',
      label: '数据未准备',
      tone: 'info',
      reason: '尚未导入 inventory_flow_history，缺货风险只能在导入后按规则计算。'
    }
  }

  const avgDailyOutbound7d = insight.totalOutbound7d / 7
  const avgDailyOutbound30d = insight.totalOutbound30d / 30
  const dailyUse = Math.max(avgDailyOutbound7d, avgDailyOutbound30d, 0)
  const replenishmentWindow = DEFAULT_LEAD_TIME_DAYS + DEFAULT_SAFETY_STOCK_DAYS
  const daysOfCover = dailyUse > 0 ? availableQty / dailyUse : null

  if (daysOfCover !== null && daysOfCover <= replenishmentWindow) {
    return {
      code: 'HIGH',
      label: '高风险',
      tone: 'danger',
      daysOfCover,
      reason: `当前可用 ${availableQty}，近 7/30 天日均出库约 ${dailyUse.toFixed(1)}，库存覆盖约 ${daysOfCover.toFixed(1)} 天，低于补货窗口 ${replenishmentWindow} 天。`
    }
  }

  if (lowStockQty > 0 && availableQty <= lowStockQty) {
    return {
      code: 'WATCH',
      label: '关注',
      tone: 'warning',
      reason: `当前可用 ${availableQty}，已低于低储阈值 ${lowStockQty}。`
    }
  }

  if (dailyUse === 0) {
    return {
      code: 'NONE',
      label: '正常',
      tone: 'success',
      reason: '近期没有出库消耗，暂未触发缺货规则。'
    }
  }

  return {
    code: 'NONE',
    label: '正常',
    tone: 'success',
    daysOfCover,
    reason: `当前可用 ${availableQty}，库存覆盖约 ${daysOfCover?.toFixed(1)} 天。`
  }
}

function buildStagnationRisk(row, insight, today) {
  const onHandQty = toNumber(row.onHandQty)
  if (onHandQty <= 0) {
    return {
      code: 'NONE',
      label: '正常',
      tone: 'success',
      reason: '当前账面库存为 0，不参与呆滞判断。'
    }
  }

  if (!insight || !insight.totalRecords) {
    return {
      code: 'DATA_UNPREPARED',
      label: '数据未准备',
      tone: 'info',
      reason: '尚未导入 inventory_flow_history，无法判断最近出库活跃度。'
    }
  }

  const now = parseDate(today) || new Date()
  const daysSinceLastOutbound = daysBetween(now, insight.lastOutboundDate)
  const inventoryAgeDays = daysBetween(now, insight.lastInboundDate)

  if ((daysSinceLastOutbound !== null && daysSinceLastOutbound >= DEFAULT_STAGNANT_DAYS && insight.totalOutbound30d === 0)
    || (daysSinceLastOutbound === null && inventoryAgeDays !== null && inventoryAgeDays >= DEFAULT_STAGNANT_DAYS)) {
    return {
      code: 'HIGH',
      label: '高风险',
      tone: 'danger',
      reason: `最近 ${daysSinceLastOutbound ?? inventoryAgeDays} 天未见有效出库，已超过默认呆滞阈值 ${DEFAULT_STAGNANT_DAYS} 天。`
    }
  }

  if (inventoryAgeDays !== null && inventoryAgeDays >= 30 && insight.totalOutbound30d < onHandQty * 0.3) {
    return {
      code: 'WATCH',
      label: '关注',
      tone: 'warning',
      reason: `库存账龄约 ${inventoryAgeDays} 天，近 30 天出库 ${insight.totalOutbound30d.toFixed(1)}，周转偏慢。`
    }
  }

  return {
    code: 'NONE',
    label: '正常',
    tone: 'success',
    reason: '近 30 天仍有正常流转，未触发呆滞规则。'
  }
}

export function buildInventoryMonitorRows({ balances = [], materials = [], flowRecords = [], today = new Date() }) {
  const materialMap = new Map(materials.map((item) => [item.materialCode, item]))
  const flowInsightMap = buildFlowInsightMap(flowRecords, today)

  return balances.map((row) => {
    const material = materialMap.get(row.materialCode) || {}
    const stockState = buildStockState(row, material)
    const shortageRisk = buildShortageRisk(row, material, flowInsightMap.get(row.materialCode))
    const stagnationRisk = buildStagnationRisk(row, flowInsightMap.get(row.materialCode), today)
    const supplier = material.supplier || {}
    const daysOfCover = shortageRisk.daysOfCover ?? null

    return {
      ...row,
      supplierId: supplier.id ?? null,
      supplierCode: supplier.supplierCode || '',
      supplierName: supplier.supplierName || '',
      lowStockQty: material.lowStockQty ?? null,
      highStockQty: material.highStockQty ?? null,
      stockState,
      shortageRisk,
      stagnationRisk,
      daysOfCover,
      warningReady: shortageRisk.code !== 'DATA_UNPREPARED' || stagnationRisk.code !== 'DATA_UNPREPARED'
    }
  })
}

export function buildWarningDataReadiness(batches = [], records = []) {
  if (!batches.length || !records.length) {
    return {
      code: 'NOT_READY',
      label: '数据未准备',
      tone: 'warning',
      reason: '尚未导入 inventory_flow_history，规则型预警会降级为“数据未准备”。'
    }
  }

  const movementTypes = new Set(records.map((item) => item.movementType).filter(Boolean))
  if (!movementTypes.has('OUTBOUND')) {
    return {
      code: 'PARTIAL',
      label: '部分准备',
      tone: 'warning',
      reason: '当前仅有静态或入库数据，缺少出库流水，缺货/呆滞规则只能部分计算。'
    }
  }

  return {
    code: 'READY',
    label: '已准备',
    tone: 'success',
    reason: `最近批次共 ${records.length} 条流水，已具备规则型预警所需的基础历史数据。`
  }
}

export function buildRiskPreviewRows(inventoryRows = [], limit = 8) {
  return inventoryRows
    .filter((row) => WARNING_RISK_CODES.has(row.shortageRisk.code) || WARNING_RISK_CODES.has(row.stagnationRisk.code))
    .sort((left, right) => {
      const leftScore = WARNING_RISK_CODES.has(left.shortageRisk.code) ? 2 : WARNING_RISK_CODES.has(left.stagnationRisk.code) ? 1 : 0
      const rightScore = WARNING_RISK_CODES.has(right.shortageRisk.code) ? 2 : WARNING_RISK_CODES.has(right.stagnationRisk.code) ? 1 : 0
      return rightScore - leftScore
    })
    .slice(0, limit)
}

export function filterInventoryTagRows(rows = [], filters = {}) {
  const keyword = filters.keyword?.trim()
  return rows.filter((row) => {
    if (filters.status && row.status !== filters.status) {
      return false
    }
    if (filters.holdType && row.activeHoldType !== filters.holdType) {
      return false
    }
    if (keyword && ![
      row.inventoryTagCode,
      row.inboundNo,
      row.materialCode,
      row.materialName
    ].some((value) => includesText(value, keyword))) {
      return false
    }
    return true
  })
}

export function filterInboundHistoryOrders(orders = [], materialsById = {}, filters = {}) {
  return orders
    .map((order) => {
      const materialCodes = (order.lines || []).map((line) => materialsById[line.materialId]?.code).filter(Boolean)
      return {
        ...order,
        materialCodes,
        materialSummary: summarizeMaterialCodes(materialCodes)
      }
    })
    .filter((order) => {
      if (filters.statuses?.length && !filters.statuses.includes(order.status)) {
        return false
      }
      if (!includesText(order.inboundNo, filters.inboundNo)) {
        return false
      }
      if (!includesText(`${order.supplier?.code || ''} ${order.supplier?.name || ''}`, filters.supplierKeyword)) {
        return false
      }
      if (filters.materialCode && !order.materialCodes.includes(filters.materialCode)) {
        return false
      }
      if (!inDateRange(order.completedAt || order.createdAt, filters.dateRange)) {
        return false
      }
      return true
    })
}

export function filterOutboundHistoryOrders(orders = [], forceLogs = [], filters = {}) {
  const forceSet = new Set()
  for (const log of forceLogs) {
    if (log?.stolenByOutboundNo) {
      forceSet.add(log.stolenByOutboundNo)
    }
    if (log?.originalOutboundNo) {
      forceSet.add(log.originalOutboundNo)
    }
  }

  return orders
    .map((order) => {
      const materialCodes = (order.lines || []).map((line) => line.materialCode).filter(Boolean)
      return {
        ...order,
        materialCodes,
        materialSummary: summarizeMaterialCodes(materialCodes),
        hasForceOutbound: forceSet.has(order.outboundNo)
      }
    })
    .filter((order) => {
      if (filters.statuses?.length && !filters.statuses.includes(order.status)) {
        return false
      }
      if (!includesText(order.outboundNo, filters.outboundNo)) {
        return false
      }
      if (!includesText(`${order.supplier?.code || ''} ${order.supplier?.name || ''}`, filters.supplierKeyword)) {
        return false
      }
      if (filters.materialCode && !order.materialCodes.includes(filters.materialCode)) {
        return false
      }
      if (filters.forceOutboundOnly && !order.hasForceOutbound) {
        return false
      }
      if (!inDateRange(order.completedAt || order.createdAt, filters.dateRange)) {
        return false
      }
      return true
    })
}
