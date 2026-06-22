import { describe, expect, it } from 'vitest'
import {
  buildInventoryMonitorRows,
  filterInboundHistoryOrders,
  filterKanbanRows,
  filterOutboundHistoryOrders
} from './monitoring'

describe('buildInventoryMonitorRows', () => {
  it('derives stock, shortage, and stagnation states from balances, materials, and flow history', () => {
    const rows = buildInventoryMonitorRows({
      balances: [
        {
          materialCode: 'MAT-001',
          materialName: '前保险杠支架',
          warehouseCode: 'WH-A',
          locationCode: 'A-01',
          onHandQty: 120,
          availableQty: 20,
          outboundLockedQty: 40,
          sealedQty: 30,
          manualLockedQty: 30,
          looseQty: 5
        },
        {
          materialCode: 'MAT-002',
          materialName: '加速踏板模块',
          warehouseCode: 'WH-A',
          locationCode: 'A-02',
          onHandQty: 10,
          availableQty: 0,
          outboundLockedQty: 0,
          sealedQty: 10,
          manualLockedQty: 0,
          looseQty: 0
        }
      ],
      materials: [
        {
          materialCode: 'MAT-001',
          materialName: '前保险杠支架',
          lowStockQty: 30,
          highStockQty: 100,
          supplier: { code: 'SUP-01', name: '华翔' }
        },
        {
          materialCode: 'MAT-002',
          materialName: '加速踏板模块',
          lowStockQty: 20,
          highStockQty: 80,
          supplier: { code: 'SUP-02', name: '吉耀' }
        }
      ],
      flowRecords: [
        { materialCode: 'MAT-001', movementType: 'OUTBOUND', quantity: 20, businessDate: '2026-06-22' },
        { materialCode: 'MAT-001', movementType: 'OUTBOUND', quantity: 15, businessDate: '2026-06-19' },
        { materialCode: 'MAT-001', movementType: 'INBOUND', quantity: 100, businessDate: '2026-05-10' },
        { materialCode: 'MAT-002', movementType: 'INBOUND', quantity: 40, businessDate: '2026-05-01' }
      ],
      today: '2026-06-23'
    })

    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({
      materialCode: 'MAT-001',
      stockState: { code: 'LOW' },
      shortageRisk: { code: 'HIGH' },
      stagnationRisk: { code: 'WATCH' },
      supplierCode: 'SUP-01'
    })
    expect(rows[0].shortageRisk.reason).toContain('库存覆盖')
    expect(rows[0].daysOfCover).toBeCloseTo(4, 1)
    expect(rows[1]).toMatchObject({
      materialCode: 'MAT-002',
      stockState: { code: 'OUT_OF_STOCK' },
      shortageRisk: { code: 'CRITICAL' },
      stagnationRisk: { code: 'HIGH' }
    })
  })

  it('marks warning data as unprepared when history is insufficient', () => {
    const rows = buildInventoryMonitorRows({
      balances: [
        {
          materialCode: 'MAT-003',
          materialName: '雨刮器',
          warehouseCode: 'WH-A',
          locationCode: 'A-03',
          onHandQty: 50,
          availableQty: 50,
          outboundLockedQty: 0,
          sealedQty: 0,
          manualLockedQty: 0,
          looseQty: 0
        }
      ],
      materials: [
        {
          materialCode: 'MAT-003',
          materialName: '雨刮器',
          lowStockQty: 10,
          highStockQty: 60,
          supplier: { code: 'SUP-03', name: '星宇' }
        }
      ],
      flowRecords: [],
      today: '2026-06-23'
    })

    expect(rows[0].shortageRisk.code).toBe('DATA_UNPREPARED')
    expect(rows[0].stagnationRisk.code).toBe('DATA_UNPREPARED')
  })
})

describe('filterKanbanRows', () => {
  it('filters kanbans by lifecycle, hold type, and text fields', () => {
    const rows = [
      { kanbanCode: 'KB-001', status: 'RECEIVED', inboundNo: 'IN-1', materialCode: 'MAT-001', activeHoldType: null },
      { kanbanCode: 'KB-002', status: 'SEALED', inboundNo: 'IN-2', materialCode: 'MAT-002', activeHoldType: 'SEALED' },
      { kanbanCode: 'KB-003', status: 'LOCKED', inboundNo: 'IN-3', materialCode: 'MAT-003', activeHoldType: 'MANUAL_LOCK' }
    ]

    expect(filterKanbanRows(rows, { holdType: 'SEALED' })).toEqual([rows[1]])
    expect(filterKanbanRows(rows, { keyword: 'KB-003', status: 'LOCKED' })).toEqual([rows[2]])
  })
})

describe('filterInboundHistoryOrders', () => {
  it('filters inbound history by supplier, material, and date range using material metadata', () => {
    const orders = [
      {
        id: 1,
        inboundNo: 'IN-001',
        status: 'COMPLETED',
        createdAt: '2026-06-20T09:00:00',
        completedAt: '2026-06-21T10:00:00',
        supplier: { code: 'SUP-01', name: '华翔' },
        lines: [{ materialId: 11 }]
      },
      {
        id: 2,
        inboundNo: 'IN-002',
        status: 'CANCELLED',
        createdAt: '2026-06-10T09:00:00',
        completedAt: null,
        supplier: { code: 'SUP-02', name: '吉耀' },
        lines: [{ materialId: 12 }]
      }
    ]
    const materialsById = {
      11: { code: 'MAT-001', name: '前保险杠支架' },
      12: { code: 'MAT-002', name: '加速踏板模块' }
    }

    const result = filterInboundHistoryOrders(orders, materialsById, {
      statuses: ['COMPLETED'],
      supplierKeyword: '华翔',
      materialCode: 'MAT-001',
      dateRange: ['2026-06-18', '2026-06-23']
    })

    expect(result).toHaveLength(1)
    expect(result[0].id).toBe(1)
    expect(result[0].materialSummary).toContain('MAT-001')
  })
})

describe('filterOutboundHistoryOrders', () => {
  it('filters outbound history by material and force-outbound audit logs', () => {
    const orders = [
      {
        id: 1,
        outboundNo: 'OUT-001',
        status: 'COMPLETED',
        createdAt: '2026-06-20T09:00:00',
        completedAt: '2026-06-21T10:00:00',
        supplier: { code: 'SUP-01', name: '华翔' },
        lines: [{ materialCode: 'MAT-001', materialName: '前保险杠支架' }]
      },
      {
        id: 2,
        outboundNo: 'OUT-002',
        status: 'CANCELLED',
        createdAt: '2026-06-10T09:00:00',
        completedAt: null,
        supplier: { code: 'SUP-02', name: '吉耀' },
        lines: [{ materialCode: 'MAT-002', materialName: '加速踏板模块' }]
      }
    ]
    const forceLogs = [{ stolenByOutboundNo: 'OUT-001', originalOutboundNo: 'OUT-LEGACY' }]

    const result = filterOutboundHistoryOrders(orders, forceLogs, {
      statuses: ['COMPLETED', 'CANCELLED'],
      materialCode: 'MAT-001',
      forceOutboundOnly: true
    })

    expect(result).toHaveLength(1)
    expect(result[0].outboundNo).toBe('OUT-001')
    expect(result[0].hasForceOutbound).toBe(true)
  })
})
